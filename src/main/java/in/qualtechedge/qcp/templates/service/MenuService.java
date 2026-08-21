package in.qualtechedge.qcp.templates.service;

import in.qualtechedge.qcp.templates.dto.response.MenuItemResponse;
import in.qualtechedge.qcp.templates.dto.response.MenuNodeResponse;
import java.util.List;

/**
 * Service interface defining the contract (QCP service rule);
 * the implementation lives in service/impl.
 */
public interface MenuService {

    /** Flat list of active, grantable menus — used by the Access Control panel. */
    List<MenuItemResponse> listGrantableMenus();

    /** Active menus assembled into a parent/child tree — used by sidebar/route registries. */
    List<MenuNodeResponse> listMenuTree();
}
