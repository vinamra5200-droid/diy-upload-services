package in.qualtechedge.qcp.templates.repository;

import in.qualtechedge.qcp.templates.entity.UploadJob;
import in.qualtechedge.qcp.templates.enums.JobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadJobRepository
        extends JpaRepository<UploadJob, String>, JpaSpecificationExecutor<UploadJob> {

    List<UploadJob> findByMakerUserIdOrderByCreatedAtDesc(String makerUserId);

    /**
     * Per-process rows-actually-processed totals for the viewer dashboard's chart — one row per
     * (processCode, processName) pair, ordered by volume. Deliberately restricted to jobs that
     * reached a terminal processing state ({@code completedStatus}/{@code failedStatus}, i.e.
     * dispatch was attempted): a still-QUEUED/PROCESSING job's rows haven't gone through the
     * pipeline yet and would overstate "processed" if included, and {@code totalRecords} on the
     * job includes rows that failed VALIDATION and were therefore never dispatched at all — so
     * this sums {@code passedRecords} (the rows that were actually sent) rather than
     * {@code totalRecords}. Result columns: processCode, processName, rows sent for processing
     * (sum of passedRecords across both terminal statuses), rows in completed jobs, rows in
     * failed jobs, count of terminal jobs.
     */
    @Query("SELECT j.processCode, j.processName, "
            + "SUM(j.passedRecords), "
            + "SUM(CASE WHEN j.status = :completedStatus THEN j.passedRecords ELSE 0 END), "
            + "SUM(CASE WHEN j.status = :failedStatus THEN j.passedRecords ELSE 0 END), "
            + "COUNT(j) "
            + "FROM UploadJob j WHERE j.status IN (:completedStatus, :failedStatus) "
            + "GROUP BY j.processCode, j.processName ORDER BY SUM(j.passedRecords) DESC")
    List<Object[]> aggregateRecordsByProcess(
            @Param("completedStatus") JobStatus completedStatus, @Param("failedStatus") JobStatus failedStatus);

    /**
     * The job created off one attempt — direct (maker-checker disabled,
     * {@code UploadAttemptServiceImpl#createDirectJob}) or via checker approval
     * ({@code CheckerServiceImpl#accept}), whichever path this attempt actually went through.
     * {@code findFirst...OrderByCreatedAtDesc} rather than a plain unique lookup: an attempt can
     * only ever produce one job today, but this stays correct rather than throwing
     * {@code NonUniqueResultException} if that ever changes.
     */
    Optional<UploadJob> findFirstByUploadAttemptIdOrderByCreatedAtDesc(String uploadAttemptId);
}
