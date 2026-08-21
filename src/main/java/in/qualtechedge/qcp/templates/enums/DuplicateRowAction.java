package in.qualtechedge.qcp.templates.enums;

/** How a template's data-load step handles duplicate primary-key rows. */
public enum DuplicateRowAction {
    reject,
    skipSilent,
    overwrite
}
