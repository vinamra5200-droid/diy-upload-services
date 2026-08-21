package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Console navigation entry ({@code auth.sidebar_menus} — db/migration/V1_0_8). Replaces the
 * retired {@code ibs.menu_master}.
 * <p>
 * {@code menuCode} and {@code sectionCode} are carried over from {@code menu_master} because
 * the console keys off them; kyc-service's own table has neither. The API keeps the old field
 * names ({@code menuLabel}, {@code routePath}, {@code iconKey}, {@code displayOrder}) — the
 * mapper renames, so the console contract is unchanged.
 */
@Entity
@Table(name = "sidebar_menus", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
public class AuthSidebarMenuEntity implements SidebarMenuView {

    public static final int STATUS_ACTIVE = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1 = menu, 2 = sub menu, 3 = sub menu item, 4..6 = deeper nesting. */
    @Column(name = "menu_type")
    private Integer menuType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "menu_code", unique = true, length = 80)
    private String menuCode;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String path;

    @Column(length = 100)
    private String icon;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "section_code", length = 60)
    private String sectionCode;

    @Column(nullable = false)
    private Integer status = STATUS_ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
