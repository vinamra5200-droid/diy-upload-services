package in.qualtechedge.qcp.templates.dto.response;

import java.util.UUID;

/** Flat grantable menu item — used by the Access Control panel (checkbox list). */
public record MenuItemResponse(
        UUID id,
        String menuCode,
        String menuLabel,
        UUID parentMenuId,
        String parentMenuLabel,
        String sectionCode,
        String routePath,
        Integer displayOrder
) {
}
