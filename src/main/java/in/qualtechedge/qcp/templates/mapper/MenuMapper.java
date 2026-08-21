package in.qualtechedge.qcp.templates.mapper;

import in.qualtechedge.qcp.templates.dto.response.MenuItemResponse;
import in.qualtechedge.qcp.templates.dto.response.MenuNodeResponse;
import in.qualtechedge.qcp.templates.entity.SidebarMenuView;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Manual DTO ⇆ entity converter for the console navigation (QCP mapper rule: mapping only).
 * <p>
 * The response field names predate the move to {@code auth.sidebar_menus} and are kept so the
 * console contract does not change: {@code title} → {@code menuLabel}, {@code path} →
 * {@code routePath}, {@code icon} → {@code iconKey}, {@code orderIndex} → {@code displayOrder}.
 */
@Component
public class MenuMapper {

    public MenuItemResponse toItemResponse(SidebarMenuView entity, Map<UUID, ? extends SidebarMenuView> byId) {
        SidebarMenuView parent = entity.getParentId() != null ? byId.get(entity.getParentId()) : null;
        return new MenuItemResponse(
                entity.getId(),
                entity.getMenuCode(),
                entity.getTitle(),
                entity.getParentId(),
                parent != null ? parent.getTitle() : null,
                entity.getSectionCode(),
                entity.getPath(),
                entity.getOrderIndex());
    }

    public MenuNodeResponse toNodeResponse(SidebarMenuView entity, List<MenuNodeResponse> children) {
        return new MenuNodeResponse(
                entity.getId(),
                entity.getMenuCode(),
                entity.getTitle(),
                entity.getParentId(),
                entity.getSectionCode(),
                entity.getPath(),
                entity.getIcon(),
                entity.getOrderIndex(),
                children == null ? Collections.emptyList() : children);
    }
}
