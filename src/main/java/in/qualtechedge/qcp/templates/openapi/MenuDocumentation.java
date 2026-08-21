package in.qualtechedge.qcp.templates.openapi;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.MenuItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "Menus", description = "Menu Master catalog used by Access Control and the sidebar registry")
public interface MenuDocumentation {

    @Operation(summary = "List grantable menus", description = "Returns the flat list of active menus for the Access Control panel.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Menus returned")
    ResponseEntity<APIResponse<List<MenuItemResponse>>> getAll();
}
