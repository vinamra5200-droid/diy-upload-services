package in.qualtechedge.qcp.templates.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

/**
 * QCP-standard response envelope (locked contract — see docs/standards/api-standards.md §3).
 * Every endpoint returns ResponseEntity&lt;APIResponse&lt;T&gt;&gt; for success and error alike.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record APIResponse<T>(
        Status status,
        Integer statusCode,
        String message,
        T data,
        String errorCode,
        String errorMessage,
        String path,
        List<ErrorDetail> errors,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime timestamp
) {
    // Static factory for success response with data
    public static <T> APIResponse<T> success(Integer statusCode, String message, T data) {
        return new APIResponse<>(
                Status.SUCCESS, statusCode, message, data,
                null, null, null, null,
                LocalDateTime.now()
        );
    }

    // Static factory for error response
    public static <T> APIResponse<T> error(Integer statusCode, String errorMessage) {
        return new APIResponse<>(
                Status.ERROR, statusCode, null, null,
                null, errorMessage, null, null,
                LocalDateTime.now()
        );
    }

    public enum Status {
        SUCCESS, ERROR
    }

    public record ErrorDetail(
            String field,
            String errorCode,
            String errorMessage
    ) {
    }
}
