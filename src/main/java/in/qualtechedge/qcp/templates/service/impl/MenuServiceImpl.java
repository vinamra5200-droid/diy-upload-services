package in.qualtechedge.qcp.templates.service.impl;

import in.qualtechedge.qcp.templates.dto.response.MenuItemResponse;
import in.qualtechedge.qcp.templates.dto.response.MenuNodeResponse;
import in.qualtechedge.qcp.templates.entity.AuthSidebarMenuEntity;
import in.qualtechedge.qcp.templates.entity.SidebarMenuView;
import in.qualtechedge.qcp.templates.entity.TenantSidebarMenuEntity;
import in.qualtechedge.qcp.templates.mapper.MenuMapper;
import in.qualtechedge.qcp.templates.multitenancy.context.HostContext;
import in.qualtechedge.qcp.templates.repository.AuthSidebarMenuRepository;
import in.qualtechedge.qcp.templates.repository.TenantSidebarMenuRepository;
import in.qualtechedge.qcp.templates.service.MenuService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    private final AuthSidebarMenuRepository authSidebarMenuRepository;
    private final TenantSidebarMenuRepository tenantSidebarMenuRepository;
    private final MenuMapper menuMapper;

    @Override
    @Transactional(readOnly = true)
    public List<MenuItemResponse> listGrantableMenus() {
        List<? extends SidebarMenuView> menus = activeMenus();
        Map<UUID, SidebarMenuView> byId = new LinkedHashMap<>();
        for (SidebarMenuView menu : menus) {
            byId.put(menu.getId(), menu);
        }
        return menus.stream().map(menu -> menuMapper.toItemResponse(menu, byId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuNodeResponse> listMenuTree() {
        List<? extends SidebarMenuView> menus = activeMenus();

        Map<UUID, List<SidebarMenuView>> childrenByParent = new LinkedHashMap<>();
        List<SidebarMenuView> roots = new ArrayList<>();
        for (SidebarMenuView menu : menus) {
            if (menu.getParentId() == null) {
                roots.add(menu);
            } else {
                childrenByParent.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
            }
        }
        return roots.stream().map(root -> buildNode(root, childrenByParent)).toList();
    }

    /**
     * The menus of whichever database this request belongs to.
     *
     * <p>A tenant reads its own {@code sidebar_menus} and the console reads
     * {@code auth.sidebar_menus}; neither reads the other, and there is no query that spans them.
     * The console's navigation is backed by the {@code auth} schema a tenant
     * database does not have, so serving it on a tenant host would produce a sidebar whose every
     * entry fails — which is why this is a different set of rows rather than a filtered view of
     * the same ones.
     */
    private List<? extends SidebarMenuView> activeMenus() {
        String tenant = HostContext.getCurrentTenant();
        if (tenant != null && !HostContext.SYSTEM_TENANT.equals(tenant)) {
            log.debug("Listing menus for tenant {}", tenant);
            return tenantSidebarMenuRepository
                    .findAllByStatusOrderByOrderIndexAsc(TenantSidebarMenuEntity.STATUS_ACTIVE);
        }
        log.debug("Listing console menus");
        return authSidebarMenuRepository
                .findAllByStatusOrderByOrderIndexAsc(AuthSidebarMenuEntity.STATUS_ACTIVE);
    }

    private MenuNodeResponse buildNode(SidebarMenuView entity,
                                       Map<UUID, List<SidebarMenuView>> childrenByParent) {
        List<SidebarMenuView> children = childrenByParent.getOrDefault(entity.getId(), List.of());
        List<MenuNodeResponse> childNodes = children.stream()
                .map(child -> buildNode(child, childrenByParent))
                .toList();
        return menuMapper.toNodeResponse(entity, childNodes);
    }
}
