package in.qualtechedge.qcp.templates.multitenancy.provisioning;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal parser for PostgreSQL JDBC URLs ({@code jdbc:postgresql://host:port/database}).
 * Used to derive the tenant DB host/port from the system datasource URL and the database
 * name from a registry {@code db_url}.
 */
public record JdbcUrl(String host, String port, String database) {

    private static final Pattern POSTGRES_URL =
            Pattern.compile("^jdbc:postgresql://([^:/]+):(\\d+)/([^?]+).*$");

    public static JdbcUrl parse(String jdbcUrl) {
        Matcher matcher = POSTGRES_URL.matcher(jdbcUrl);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported JDBC URL format: " + jdbcUrl);
        }
        return new JdbcUrl(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    /** Builds a PostgreSQL JDBC URL for the given database on this host/port. */
    public String withDatabase(String databaseName) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName;
    }
}
