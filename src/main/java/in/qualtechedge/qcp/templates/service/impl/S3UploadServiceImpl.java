package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.request.PipelineAuditEventRequest;
import in.qualtechedge.qcp.templates.dto.response.UploadCountsResponse;
import in.qualtechedge.qcp.templates.dto.response.UploadFileResponse;
import in.qualtechedge.qcp.templates.entity.Template;
import in.qualtechedge.qcp.templates.entity.UploadFile;
import in.qualtechedge.qcp.templates.enums.AuditEventCode;
import in.qualtechedge.qcp.templates.enums.AuditOutcome;
import in.qualtechedge.qcp.templates.enums.UploadFileStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;
import in.qualtechedge.qcp.templates.exception.ResourceNotFoundException;
import in.qualtechedge.qcp.templates.mapper.UploadFileMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.TemplateRepository;
import in.qualtechedge.qcp.templates.repository.UploadFileRepository;
import in.qualtechedge.qcp.templates.repository.UploadProcessRepository;
import in.qualtechedge.qcp.templates.service.AuditEventService;
import in.qualtechedge.qcp.templates.service.S3UploadService;
import in.qualtechedge.qcp.templates.service.UploadEventPublisher;
import in.qualtechedge.qcp.templates.utils.CurrentActor;
import in.qualtechedge.qcp.templates.utils.IdGenerator;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Front door for maker uploads: validates the target, stages the file to a local temp file while
 * computing its SHA-256 checksum in one streaming pass — never {@link MultipartFile#getBytes()};
 * a maker's file can run to lakhs of rows, and buffering the whole thing into JVM heap doesn't
 * scale — rejects a repeat of an already-uploaded file, records a {@code pending} row, and hands
 * the actual S3 PUT off to {@link UploadS3Worker} so this call returns without waiting for it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3UploadServiceImpl implements S3UploadService {

    private final UploadFileRepository uploadFileRepository;
    private final UploadProcessRepository uploadProcessRepository;
    private final TemplateRepository templateRepository;
    private final UploadFileMapper uploadFileMapper;
    private final UploadEventPublisher uploadEventPublisher;
    private final UploadS3Worker uploadS3Worker;
    private final AuditEventService auditEventService;

    @Override
    public UploadFileResponse upload(String processId, String templateId, MultipartFile file) {
        log.debug("Accepting upload: processId={}, templateId={}, filename={}", processId, templateId, file.getOriginalFilename());
        if (file.isEmpty()) {
            recordFileRejected(processId, null, null, "Uploaded file is empty");
            throw new ConflictException("Uploaded file is empty");
        }
        assertProcessExists(processId);
        Template template = assertTemplateBelongsToProcess(templateId, processId);
        String filename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType();

        Path tempFile = tempFilePath();
        String checksum;
        long size;
        try {
            checksum = spoolAndDigest(file, tempFile);
            size = Files.size(tempFile);
        } catch (IOException e) {
            deleteQuietly(tempFile);
            throw new IllegalStateException("Failed to stage the uploaded file", e);
        }

        uploadFileRepository.findFirstByTemplateIdAndChecksumSha256AndStatusNot(templateId, checksum, UploadFileStatus.failed)
                .ifPresent(existing -> {
                    deleteQuietly(tempFile);
                    recordFileRejected(processId, template, checksum,
                            "Duplicate checksum " + checksum + " — matches upload " + existing.getUploadId()
                                    + " (status " + existing.getStatus() + ")");
                    throw new ConflictException("This file was already uploaded for this template (checksum " + checksum
                            + " matches upload " + existing.getUploadId() + ", status " + existing.getStatus() + ")");
                });

        UploadFile record = new UploadFile();
        record.setUploadId(IdGenerator.generate("rawupl"));
        record.setProcessId(processId);
        record.setTemplateId(templateId);
        record.setOriginalFilename(filename);
        record.setChecksumSha256(checksum);
        record.setFileSizeBytes(size);
        record.setContentType(contentType);
        record.setStatus(UploadFileStatus.pending);
        record.setUploadedBy(CurrentActor.id());
        UploadFile saved = uploadFileRepository.saveAndFlush(record);

        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.FILE_RECEIVED, CurrentActor.id(), null, processId,
                template.getTemplateCode(), template.getVersion(), null, null, null, null,
                AuditOutcome.SUCCESS, "Raw file received: " + filename,
                Map.of("uploadId", saved.getUploadId(), "checksumSha256", checksum, "sizeBytes", size)));

        // HostContext is thread-local (QCC Multi-Tenancy §3) and doesn't survive the @Async hop,
        // so the tenant is captured here on the request thread and handed to the worker explicitly.
        uploadS3Worker.process(HostContext.getCurrentTenant(), saved.getUploadId(), filename, contentType, tempFile);

        return uploadFileMapper.toResponse(saved);
    }

    /** FILE_REJECTED (SD §12.3 #6) — {@code template} is null for the empty-file branch, which is
     * rejected before the template/checksum are even known. */
    private void recordFileRejected(String processId, Template template, String checksum, String reason) {
        auditEventService.record(new PipelineAuditEventRequest(
                AuditEventCode.FILE_REJECTED, CurrentActor.id(), null, processId,
                template == null ? null : template.getTemplateCode(),
                template == null ? null : template.getVersion(),
                null, null, null, null,
                AuditOutcome.FAILURE, reason,
                checksum == null ? null : Map.of("checksumSha256", checksum)));
    }

    @Override
    public SseEmitter subscribe(String uploadId) {
        log.debug("Subscribing to upload events: id={}", uploadId);
        UploadFile record = uploadFileRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with id: " + uploadId));
        SseEmitter emitter = uploadEventPublisher.subscribe(uploadId);
        // Covers the race where the worker already finished before the client opened this
        // connection — the subscriber's first event is always the current state.
        uploadEventPublisher.publish(uploadFileMapper.toResponse(record));
        return emitter;
    }

    @Override
    @Transactional(readOnly = true)
    public UploadFileResponse getById(String uploadId) {
        log.debug("Fetching upload: id={}", uploadId);
        return uploadFileMapper.toResponse(uploadFileRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload not found with id: " + uploadId)));
    }

    @Override
    @Transactional(readOnly = true)
    public UploadCountsResponse counts(String processId, String templateId) {
        log.debug("Counting uploads: processId={}, templateId={}", processId, templateId);
        assertProcessExists(processId);
        Specification<UploadFile> scope = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("processId"), processId));
            if (templateId != null && !templateId.isBlank()) {
                predicates.add(cb.equal(root.get("templateId"), templateId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        long pending = uploadFileRepository.count(withStatus(scope, UploadFileStatus.pending));
        long inProgress = uploadFileRepository.count(withStatus(scope, UploadFileStatus.inProgress));
        long completed = uploadFileRepository.count(withStatus(scope, UploadFileStatus.completed));
        long failed = uploadFileRepository.count(withStatus(scope, UploadFileStatus.failed));
        return new UploadCountsResponse(processId, templateId, pending, inProgress, completed, failed,
                pending + inProgress + completed + failed);
    }

    private Specification<UploadFile> withStatus(Specification<UploadFile> scope, UploadFileStatus status) {
        return scope.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }

    private Path tempFilePath() {
        return Path.of(System.getProperty("java.io.tmpdir"), "diy-upload-" + UUID.randomUUID() + ".tmp");
    }

    /** Streams the multipart body straight to disk while hashing it — one pass, bounded memory. */
    private String spoolAndDigest(MultipartFile file, Path tempFile) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
        try (InputStream in = file.getInputStream();
             DigestInputStream digestIn = new DigestInputStream(in, digest);
             OutputStream out = Files.newOutputStream(tempFile)) {
            digestIn.transferTo(out);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp upload file: {}", path, e);
        }
    }

    /** Strips any directory component so a client can't extend the key past the intended {@code raw/} segment. */
    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new ConflictException("A filename is required");
        }
        String normalized = originalFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = (lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized).trim();
        if (name.isEmpty()) {
            throw new ConflictException("A filename is required");
        }
        return name;
    }

    private void assertProcessExists(String processId) {
        if (!uploadProcessRepository.existsById(processId)) {
            throw new ResourceNotFoundException("Process not found with id: " + processId);
        }
    }

    private Template assertTemplateBelongsToProcess(String templateId, String processId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + templateId));
        if (!template.getProcessId().equals(processId)) {
            throw new ResourceNotFoundException("Template " + templateId + " does not belong to process " + processId);
        }
        return template;
    }
}
