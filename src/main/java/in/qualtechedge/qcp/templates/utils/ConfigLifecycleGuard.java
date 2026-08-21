package in.qualtechedge.qcp.templates.utils;

import in.qualtechedge.qcp.templates.enums.ConfigStatus;
import in.qualtechedge.qcp.templates.exception.ConflictException;

/**
 * Shared maker-checker state-machine checks reused by every governed resource's create/update/
 * submit/accept/reject methods (processes, templates, roles, users, storage/database
 * connections, API configs) — admin-api-contract.md §"Config Status Lifecycle":
 * {@code draft/rejected -> waitingForChecker -> active}, or {@code -> rejected -> draft}.
 * Seven near-identical state machines is real duplication; each resource's own ServiceImpl still
 * owns its transitions and calls these directly rather than going through a generic base class.
 */
public final class ConfigLifecycleGuard {

    private ConfigLifecycleGuard() {
    }

    /** Submit requires draft or rejected status. */
    public static void assertSubmittable(ConfigStatus status) {
        if (status != ConfigStatus.draft && status != ConfigStatus.rejected) {
            throw new ConflictException(
                    "Only items in draft or rejected status can be submitted for review; current status is " + status);
        }
    }

    /** Accept/reject require waitingForChecker status. */
    public static void assertWaitingForChecker(ConfigStatus status) {
        if (status != ConfigStatus.waitingForChecker) {
            throw new ConflictException(
                    "Only items awaiting checker review can be accepted or rejected; current status is " + status);
        }
    }

    /** Four-eyes principle: the accepting/rejecting actor must differ from the submitter. */
    public static void assertFourEyes(String submittedBy, String actorId) {
        if (submittedBy != null && submittedBy.equals(actorId)) {
            throw new ConflictException("The submitter cannot accept or reject their own submission");
        }
    }

    /** Create/update preconditions shared by every resource: not mid-review. */
    public static void assertEditable(ConfigStatus status) {
        if (status == ConfigStatus.waitingForChecker) {
            throw new ConflictException("Item awaiting checker review cannot be edited; current status is " + status);
        }
    }
}
