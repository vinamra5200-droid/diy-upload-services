package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Paginated payload shape for {@code data} on collection endpoints (api-standards.md §5).
 * Wire pagination is 1-based ({@code page}/{@code limit} query params); {@link #from(Page)}
 * takes a 0-based Spring {@link Page} and reports both consistently.
 */
public record PageResponse<T>(List<T> content, PageMeta page) {

    public record PageMeta(int number, int size, long totalElements, int totalPages) {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(),
                new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
