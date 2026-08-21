package in.qualtechedge.qcp.templates.enums;

/**
 * Shared maker-checker lifecycle for processes, templates, roles, users, storage/database
 * connections and API configs (admin-api-contract.md §"Config Status Lifecycle"). Constant names
 * match the wire/DB value exactly so {@code @Enumerated(EnumType.STRING)} and Jackson's default
 * enum handling both work with no custom mapping.
 */
public enum ConfigStatus {
    draft,
    waitingForChecker,
    active,
    rejected
}
