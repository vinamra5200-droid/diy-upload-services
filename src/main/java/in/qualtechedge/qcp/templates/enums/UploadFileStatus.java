package in.qualtechedge.qcp.templates.enums;

/** Lifecycle of one raw file upload to the interim object store ({@code upload_files.status}). */
public enum UploadFileStatus {
    pending,
    inProgress,
    completed,
    failed
}
