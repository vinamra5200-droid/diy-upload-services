package in.qualtechedge.qcp.templates.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tenant's own navigation, from {@code sidebar_menus} in that tenant's database
 * (db/tenant/V1_0_7, seeded by V1_1_2).
 *
 * <p>Deliberately unqualified: no {@code schema} attribute, so it resolves in whatever database
 * the current tenant's connection is pointed at. {@link AuthSidebarMenuEntity} names the
 * {@code auth} schema and is the console's; the two never read each other, which is the whole
 * point — a tenant sees what is in its own database and nothing else.
 *
 * <p>Not a subclass of the console entity, and not shared with it. They happen to have the same
 * columns today; they are not the same table, and a change to one is not a change to the other.
 */
@Entity
@Table(name = "sidebar_menus")
@Getter
@Setter
@NoArgsConstructor
public class TenantSidebarMenuEntity implements SidebarMenuView {

    public static final int STATUS_ACTIVE = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "menu_type")
    private Integer menuType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "menu_code", length = 80)
    private String menuCode;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "path", length = 255)
    private String path;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "order_index")
    private Integer orderIndex;

    @Column(name = "section_code", length = 60)
    private String sectionCode;

    @Column(name = "status", nullable = false)
    private Integer status = STATUS_ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
