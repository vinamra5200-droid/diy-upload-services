package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.MenuItemResponse;
import in.qualtechedge.qcp.templates.openapi.MenuDocumentation;
import in.qualtechedge.qcp.templates.service.MenuService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
@Slf4j
public class MenuController implements MenuDocumentation {

    private final MenuService menuService;

    @Override
    @GetMapping
    public ResponseEntity<APIResponse<List<MenuItemResponse>>> getAll() {
        log.info("List grantable menus request");
        List<MenuItemResponse> responses = menuService.listGrantableMenus();
        log.info("Grantable menus retrieved: count={}", responses.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", responses));
    }
}
