package in.qualtechedge.qcp.templates.entity;

import java.util.UUID;

/**
 * The shape of a sidebar menu, whichever database it came out of.
 *
 * <p>There are two tables and there have to be: {@code auth.sidebar_menus} in the system database
 * is the console's navigation, and an unqualified {@code sidebar_menus} in each tenant database is
 * that tenant's. They cannot be one entity because the schema is part of the mapping and a tenant
 * database has no {@code auth} schema — and they must not be one query, because a tenant reads its
 * own database and nothing else.
 *
 * <p>This interface is what keeps that from meaning two of everything above it. The mapper and the
 * tree builder work on the view, so the console and a tenant get the same rendering from different
 * rows without either knowing about the other.
 */
public interface SidebarMenuView {

    UUID getId();

    Integer getMenuType();

    UUID getParentId();

    String getMenuCode();

    String getTitle();

    String getDescription();

    String getPath();

    String getIcon();

    Integer getOrderIndex();

    String getSectionCode();
}
