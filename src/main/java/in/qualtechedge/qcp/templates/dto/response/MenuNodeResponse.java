package in.qualtechedge.qcp.templates.dto.response;

import java.util.List;
import java.util.UUID;

/** Hierarchical menu node — {@code children} is populated when returning a tree (listMenuTree). */
public record MenuNodeResponse(
        UUID id,
        String menuCode,
        String menuLabel,
        UUID parentMenuId,
        String sectionCode,
        String routePath,
        String iconKey,
        Integer displayOrder,
        List<MenuNodeResponse> children
) {
}
