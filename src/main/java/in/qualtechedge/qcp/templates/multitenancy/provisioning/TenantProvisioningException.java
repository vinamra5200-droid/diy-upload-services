package in.qualtechedge.qcp.templates.multitenancy.provisioning;

/** Raised when provisioning or migrating a tenant database fails. */
public class TenantProvisioningException extends RuntimeException {

    public TenantProvisioningException(String message) {
        super(message);
    }

    public TenantProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
