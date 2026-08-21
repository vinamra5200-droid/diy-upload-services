#!/usr/bin/env bash
# Turn this template into a new project. One command, and the result runs.
#
#   script/rename-project.sh --package in.qualtechedge.billing \
#                            --artifact billing-service \
#                            --db-prefix billing
#
# That renames the package, artifact, main class and database names, and fills in the values a
# service cannot start without: the product segment of the host grammar, the tenant registry and
# its credentials, the CORS patterns, and the seed email addresses.
#
# Optional, when you already know them:
#   --product billing              host grammar {tenant}-{product}-{env}; default: artifact
#                                  without a trailing -service/-api/-backend
#   --tenants qc,at1               tenant short codes; default: qc. Under identity-portal-service
#                                  these are the portal's APPLICATION TENANT codes, spelled its way
#   --email-domain qualtechedge.com   seed addresses; default: qualtechedge.com
#   --db-port 5433                 local PostgreSQL port; default 5432. The identity-portal
#                                  local stack runs on 5433, because a native PostgreSQL
#                                  usually already owns 5432 on a developer machine
#   --platform-tenant qc           wire local identity to identity-portal-service
#   --realm qc-identity-local        (both, or neither — see RUN-WITH-IDENTITY-PORTAL.md)
#   --drop-example                 delete the Example CRUD slice and its two migrations
#   --dry-run                      show what would be touched, write nothing
#
# Run it once, on a fresh clone, before writing any code — it rewrites every source file, so
# doing it later means rewriting your own work too.
#
# What it still does NOT decide: what your product's tables are, and whether the Example slice
# is replaced or deleted. NEW-PROJECT.md covers both, and this script prints what is left.
set -euo pipefail

# ---- what the template currently calls itself -------------------------------------------
OLD_PACKAGE="in.qualtechedge.qcp.templates"
OLD_ARTIFACT="java-springboot-multitenant-template"
OLD_DB_PREFIX="mt-template"
OLD_APP_CLASS="TemplateServiceApplication"

NEW_PACKAGE=""
NEW_ARTIFACT=""
NEW_DB_PREFIX=""
NEW_APP_CLASS=""
PRODUCT=""
TENANTS=""
EMAIL_DOMAIN="qualtechedge.com"
DB_PORT="5432"
PLATFORM_TENANT=""
REALM=""
DROP_EXAMPLE=""
DRY_RUN=""

usage() {
    sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-1}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --package)         NEW_PACKAGE="${2:?--package needs a value}"; shift 2 ;;
        --artifact)        NEW_ARTIFACT="${2:?--artifact needs a value}"; shift 2 ;;
        --db-prefix)       NEW_DB_PREFIX="${2:?--db-prefix needs a value}"; shift 2 ;;
        --app-class)       NEW_APP_CLASS="${2:?--app-class needs a value}"; shift 2 ;;
        --product)         PRODUCT="${2:?--product needs a value}"; shift 2 ;;
        --tenants)         TENANTS="${2:?--tenants needs a value}"; shift 2 ;;
        --email-domain)    EMAIL_DOMAIN="${2:?--email-domain needs a value}"; shift 2 ;;
        --db-port)         DB_PORT="${2:?--db-port needs a value}"; shift 2 ;;
        --platform-tenant) PLATFORM_TENANT="${2:?--platform-tenant needs a value}"; shift 2 ;;
        --realm)           REALM="${2:?--realm needs a value}"; shift 2 ;;
        --drop-example)    DROP_EXAMPLE=yes; shift ;;
        --dry-run)         DRY_RUN=yes; shift ;;
        -h|--help)         usage 0 ;;
        *) echo "unknown argument: $1" >&2; usage ;;
    esac
done

[ -n "$NEW_PACKAGE" ]   || { echo "--package is required" >&2; usage; }
[ -n "$NEW_ARTIFACT" ]  || { echo "--artifact is required" >&2; usage; }
[ -n "$NEW_DB_PREFIX" ] || { echo "--db-prefix is required" >&2; usage; }

# ---- validate, because a bad value here is silent and everywhere ------------------------
case "$NEW_PACKAGE" in
    *[!a-z0-9._]*|.*|*.|*..*) echo "--package must be a lowercase Java package: $NEW_PACKAGE" >&2; exit 1 ;;
esac
case "$NEW_PACKAGE" in *.*) ;; *) echo "--package needs at least two segments" >&2; exit 1 ;; esac
case "$NEW_ARTIFACT" in
    *[!a-z0-9-]*|-*|*-) echo "--artifact must be lowercase with dashes: $NEW_ARTIFACT" >&2; exit 1 ;;
esac
# The prefix becomes a PostgreSQL database name, so it must be a legal identifier start and
# must not collide with the reserved shape that would need quoting on every psql call.
case "$NEW_DB_PREFIX" in
    *[!a-z0-9-]*|-*|*-|[0-9]*) echo "--db-prefix must be lowercase, start with a letter: $NEW_DB_PREFIX" >&2; exit 1 ;;
esac

# Default the main class from the artifact: billing-service -> BillingServiceApplication.
if [ -z "$NEW_APP_CLASS" ]; then
    NEW_APP_CLASS="$(echo "$NEW_ARTIFACT" | awk -F- '{for(i=1;i<=NF;i++) printf "%s%s", toupper(substr($i,1,1)), substr($i,2)}')Application"
fi
case "$NEW_APP_CLASS" in
    [A-Z]*) ;; *) echo "--app-class must start with a capital: $NEW_APP_CLASS" >&2; exit 1 ;;
esac

# The product is a segment of a hostname, so it cannot carry a hyphen: the grammar is
# {tenant}-{product}-{env} and a hyphen inside one segment makes the whole host ambiguous.
if [ -z "$PRODUCT" ]; then
    PRODUCT="$(echo "$NEW_ARTIFACT" | sed -E 's/-(service|api|backend|svc)$//')"
fi
case "$PRODUCT" in
    *[!a-z0-9]*|"") echo "--product must be lowercase alphanumeric, no hyphens: $PRODUCT" >&2; exit 1 ;;
esac

case "$DB_PORT" in
    *[!0-9]*|"") echo "--db-port must be a number: $DB_PORT" >&2; exit 1 ;;
esac

[ -n "$TENANTS" ] || TENANTS="qc"
TENANT_LIST="$(echo "$TENANTS" | tr ',' ' ' | tr -s ' ')"
for t in $TENANT_LIST; do
    case "$t" in
        *[!a-z0-9]*) echo "tenant short codes must be lowercase alphanumeric, no hyphens: $t" >&2; exit 1 ;;
    esac
done

# Both or neither: a realm without a platform tenant is a service running its own Keycloak, and
# a platform tenant without a realm has nowhere to verify a token against.
if [ -n "$PLATFORM_TENANT" ] && [ -z "$REALM" ]; then
    echo "--platform-tenant needs --realm (the realm identity-portal-service provisioned)" >&2; exit 1
fi
if [ -n "$REALM" ] && [ -z "$PLATFORM_TENANT" ]; then
    echo "--realm needs --platform-tenant, or the audience is never checked" >&2; exit 1
fi

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

OLD_PATH="${OLD_PACKAGE//./\/}"
NEW_PATH="${NEW_PACKAGE//./\/}"

echo "  package    $OLD_PACKAGE  ->  $NEW_PACKAGE"
echo "  artifact   $OLD_ARTIFACT  ->  $NEW_ARTIFACT"
echo "  db prefix  $OLD_DB_PREFIX  ->  $NEW_DB_PREFIX"
echo "  main class $OLD_APP_CLASS  ->  $NEW_APP_CLASS"
printf '  %-10s %s\n' product "$PRODUCT   (hosts: {tenant}-$PRODUCT-{env})"
printf '  %-10s %s\n' tenants "$TENANT_LIST"
printf '  %-10s %s\n' "seed mail" "<name>@$EMAIL_DOMAIN"
printf '  %-10s %s\n' "local db" "localhost:$DB_PORT"
if [ -n "$PLATFORM_TENANT" ]; then
    echo "  identity   identity-portal-service, realm $REALM, platform tenant $PLATFORM_TENANT"
else
    echo "  identity   this service's own Keycloak (no --platform-tenant given)"
fi
echo

# Everything except build output, logs and git internals. Held in a variable so the dry run
# counts exactly what the real run would edit.
files() {
    find . -type f \
        -not -path './.git/*' \
        -not -path './target/*' \
        -not -path './logs/*' \
        -not -name '*.jar' \
        -not -name '*.class' \
        -not -name '*.png' \
        -not -name '*.pyc'
}

if [ -n "$DRY_RUN" ]; then
    echo "dry run — nothing will be written"
    for token in "$OLD_PACKAGE" "$OLD_ARTIFACT" "$OLD_DB_PREFIX" "$OLD_APP_CLASS"; do
        n=$(files | xargs grep -l -F "$token" 2>/dev/null | wc -l | tr -d ' ')
        printf '  %-45s %s file(s)\n' "$token" "$n"
    done
    exit 0
fi

# Move a path, keeping git's history when git is actually tracking it.
#
# Being inside a repository is not the same as being tracked, and `git mv` fails on an untracked
# path with "fatal: source directory is empty" — which names neither the file nor the reason.
# Both ways of starting a project produce exactly that: `git init` on a fresh clone leaves
# everything untracked, and copying the template into an existing empty repository does too. So
# the check has to be per path, not per repository.
move_path() {
    if git -C "$ROOT" ls-files --error-unmatch "$1" >/dev/null 2>&1; then
        git -C "$ROOT" mv "$1" "$2"
    else
        mv "$1" "$2"
    fi
}

# Rewrite a file through a filter, keeping the original only until the new one is complete —
# a half-written config is worse than an unwritten one.
rewrite() {
    local file="$1"; shift
    [ -f "$file" ] || return 0
    "$@" < "$file" > "$file.tmp" && mv "$file.tmp" "$file"
}

# ---- move the package directories first, so the rewrite below sees final paths -----------
for base in src/main/java src/test/java; do
    [ -d "$base/$OLD_PATH" ] || continue
    mkdir -p "$base/$(dirname "$NEW_PATH")"
    move_path "$base/$OLD_PATH" "$base/$NEW_PATH"
    # Drop the directories the old package left behind, but only while they are empty —
    # another project may legitimately live alongside under a shared prefix.
    old_parent="$(dirname "$base/$OLD_PATH")"
    while [ "$old_parent" != "$base" ] && [ -d "$old_parent" ] && [ -z "$(ls -A "$old_parent")" ]; do
        rmdir "$old_parent"
        old_parent="$(dirname "$old_parent")"
    done
done

# ---- rewrite contents --------------------------------------------------------------------
# Order matters: the class name is rewritten before the package, because the package rewrite
# would otherwise leave the old class sitting in the new package and the mismatch only shows
# up as a compile error pointing at the wrong file.
files | while IFS= read -r f; do
    sed -i \
        -e "s/${OLD_APP_CLASS}/${NEW_APP_CLASS}/g" \
        -e "s/${OLD_PACKAGE}/${NEW_PACKAGE}/g" \
        -e "s/${OLD_ARTIFACT}/${NEW_ARTIFACT}/g" \
        -e "s/${OLD_DB_PREFIX}/${NEW_DB_PREFIX}/g" \
        "$f"
done

# ---- rename the main class file ----------------------------------------------------------
for base in src/main/java src/test/java; do
    old_file="$base/$NEW_PATH/${OLD_APP_CLASS}.java"
    [ -f "$old_file" ] || continue
    move_path "$old_file" "$base/$NEW_PATH/${NEW_APP_CLASS}.java"
done

# =========================================================================================
# Project values. Everything below used to be a hand-edit listed in NEW-PROJECT.md, and each
# one of them is required before the service can serve a single request: with no product the
# host grammar matches nothing, with no tenant credentials provisioning fails at boot, and with
# the wrong CORS patterns sign-in fails in the browser with no useful server-side trace.
# =========================================================================================

# ---- product: the middle segment of every hostname this service answers to ---------------
sed -i "s|^\( *product: \${QCP_HOST_PRODUCT\):}|\1:$PRODUCT}|" src/main/resources/application.yaml
sed -i "s|<product>|$PRODUCT|g; s|^QCP_HOST_PRODUCT=$|QCP_HOST_PRODUCT=$PRODUCT|" .env.example

# ---- local database port -----------------------------------------------------------------
# Tenant database URLs are derived from the system datasource URL at runtime, so they follow
# this automatically; the Flyway placeholder is what the registry rows are seeded with.
if [ "$DB_PORT" != "5432" ]; then
    sed -i \
        -e "s|jdbc:postgresql://localhost:5432/|jdbc:postgresql://localhost:$DB_PORT/|" \
        -e "s|^\( *db_port: \)5432$|\1$DB_PORT|" \
        src/main/resources/application-local.yaml
    sed -i \
        -e "s|jdbc:postgresql://localhost:5432/|jdbc:postgresql://localhost:$DB_PORT/|" \
        -e "s|^DB_PORT=5432$|DB_PORT=$DB_PORT|" \
        .env.example
fi

# ---- tenant registry, credentials and CORS ----------------------------------------------
# The registry seed. Every environment's codes belong here, not only the first one stood up:
# a registry that changes shape per environment is how a service works locally and 403s on a
# server. Names are placeholders — the code is the part that must be exact.
rows=""
for t in $TENANT_LIST; do
    [ -z "$rows" ] || rows="$rows,
"
    rows="$rows('Tenant $t', 'Tenant $t', '$t', 'jdbc:postgresql://\${db_host}:\${db_port}/$NEW_DB_PREFIX-$t-db', 1)"
done
rows="INSERT INTO tenant.tenants (name, description, short_code, db_url, status) VALUES
$rows;"
rewrite src/main/resources/db/migration/V1_1_20__seed_tenants.sql \
    awk -v rows="$rows" '
        /^INSERT INTO tenant\.tenants/ { print rows; skipping = 1; next }
        skipping { if (/;[ \t]*$/) skipping = 0; next }
        { print }'

# Local per-tenant database credentials, and the CORS patterns for the local host family.
# These are role names the provisioner creates, never existing logins.
{
    echo "qcp:"
    echo "  security:"
    echo "    cors:"
    echo "      # Origin *patterns*, so onboarding a tenant needs no code change and no redeploy:"
    echo "      # one line covers every tenant under the subdomain convention."
    echo "      allowed-origin-patterns: https://*-$PRODUCT-local.qualtechedge.in,http://localhost:*,https://localhost:*"
    echo "  multitenancy:"
    echo "    # Per-tenant DB credentials — local only; server environments use env vars (and Vault"
    echo "    # later). The provisioning service creates each role with this password and makes it"
    echo "    # the owner of that tenant's database, so these are role names it creates rather than"
    echo "    # existing logins. Never reuse the service's own database user: the provisioner would"
    echo "    # reset the password of the role it is connected as, mid-boot."
    echo "    #"
    echo "    # The role name carries the product for the same reason. Several QCP services share"
    echo "    # one local PostgreSQL and they share tenant codes too, so a bare '<tenant>_user'"
    echo "    # is the same role in all of them — and each service's provisioner refreshes that"
    echo "    # role's password at boot, quietly breaking whichever service started first."
    echo "    #"
    echo "    # A tenant needs credentials here before it can be onboarded live, with no restart:"
    echo "    #   POST /api/v1/admin/tenants {\"name\":\"...\",\"shortCode\":\"...\"}"
    echo "    tenants:"
    for t in $TENANT_LIST; do
        echo "      $t:"
        echo "        db-username: ${PRODUCT}_${t}_user"
        echo "        db-password: ${PRODUCT}_${t}_pass"
    done
} > /tmp/qcp-block.$$
sed -n '/^qcp:$/q;p' src/main/resources/application-local.yaml > src/main/resources/application-local.yaml.tmp
cat /tmp/qcp-block.$$ >> src/main/resources/application-local.yaml.tmp
mv src/main/resources/application-local.yaml.tmp src/main/resources/application-local.yaml
rm -f /tmp/qcp-block.$$

# Local Keycloak client secrets are keyed by tenant short code, so they follow the same list.
secrets=""
for t in $TENANT_LIST; do
    [ -z "$secrets" ] || secrets="$secrets
"
    secrets="$secrets      $t: secret"
done
rewrite src/main/resources/application-local.yaml \
    awk -v block="$secrets" '
        /^    client-secrets:$/ { print; print block; inmap = 1; next }
        inmap && /^      / { next }
        { inmap = 0; print }'

# The same codes as environment variables, for the server profiles.
envpairs=""
for t in $TENANT_LIST; do
    u="$(echo "$t" | tr '[:lower:]' '[:upper:]')"
    [ -z "$envpairs" ] || envpairs="$envpairs
"
    envpairs="${envpairs}QCP_MULTITENANCY_TENANTS_${u}_DBUSERNAME=${PRODUCT}_${t}_user
QCP_MULTITENANCY_TENANTS_${u}_DBPASSWORD=${PRODUCT}_${t}_pass"
done
rewrite .env.example \
    awk -v block="$envpairs" '
        /^QCP_MULTITENANCY_TENANTS_/ { if (!done) { print block; done = 1 } next }
        { print }'

composepairs="$(echo "$envpairs" | sed 's/^/      - /')"
rewrite docker-compose.yml \
    awk -v block="$composepairs" '
        /QCP_MULTITENANCY_TENANTS_/ { if (!done) { print block; done = 1 } next }
        { print }'

# ---- seed addresses ----------------------------------------------------------------------
# email_id and mobile_number are UNIQUE, and the product prefix is what keeps two services
# seeded into one database from colliding on them.
for f in src/main/resources/db/migration/V1_1_0__seed_users.sql \
         src/main/resources/db/tenant/V1_1_0__seed_admin_user.sql \
         src/main/resources/db/tenant/V1_1_2__seed_api_clients.sql; do
    [ -f "$f" ] || continue
    sed -i -E "s/'([^']*)@example\.com'/'$PRODUCT-\1@$EMAIL_DOMAIN'/g" "$f"
done

# ---- identity ----------------------------------------------------------------------------
# Only the local profile is written: the server profiles read KEYCLOAK_REALM and
# KEYCLOAK_PLATFORM_TENANT from the environment already.
if [ -n "$PLATFORM_TENANT" ]; then
    sed -i \
        -e "s|^    realm: master$|    realm: \${KEYCLOAK_REALM:$REALM}|" \
        -e "s|^    super-admin-realm: admin-local$|    super-admin-realm: \${KEYCLOAK_SUPER_ADMIN_REALM:$REALM}|" \
        -e "/^    realm-ssl-required: /a\\    # The platform tenant identity-portal-service issued these tokens for: the\\n    # \`$PLATFORM_TENANT\` in \`oauth-client-<apptenant>-$PRODUCT-$PLATFORM_TENANT\`. Setting it turns\\n    # audience checking on, which is the only thing separating two application\\n    # tenants once they share a realm.\\n    platform-tenant: \${KEYCLOAK_PLATFORM_TENANT:$PLATFORM_TENANT}" \
        src/main/resources/application-local.yaml
    sed -i \
        -e "s|^KEYCLOAK_REALM=$|KEYCLOAK_REALM=$REALM|" \
        -e "s|^KEYCLOAK_PLATFORM_TENANT=$|KEYCLOAK_PLATFORM_TENANT=$PLATFORM_TENANT|" \
        .env.example
fi

# ---- the Example slice --------------------------------------------------------------------
if [ -n "$DROP_EXAMPLE" ]; then
    find src -name 'Example*.java' -exec rm -f {} +
    rm -f src/main/resources/db/tenant/V1_0_30__create_example_entity.sql \
          src/main/resources/db/tenant/V1_1_30__seed_example_entity.sql
    echo "  dropped the Example slice — V1_0_30 and V1_1_30 are yours to use"
fi

# ---- verify ------------------------------------------------------------------------------
# A rename that half worked is worse than one that failed: it compiles, and the leftover only
# surfaces as a database named after the template months later.
echo
leftovers=0
for token in "$OLD_PACKAGE" "$OLD_ARTIFACT" "$OLD_DB_PREFIX" "$OLD_APP_CLASS"; do
    hits=$(files | xargs grep -l -F "$token" 2>/dev/null || true)
    if [ -n "$hits" ]; then
        echo "STILL PRESENT: $token"
        echo "$hits" | sed 's/^/    /'
        leftovers=1
    fi
done

# The values that are not substitutions but are still required to serve a request. Each of
# these failed silently in a real project before the script filled it in.
if grep -q 'product: ${QCP_HOST_PRODUCT:}' src/main/resources/application.yaml; then
    echo "STILL BLANK: qcp.multitenancy.host.product — every host would resolve to nothing"
    leftovers=1
fi
# Scoped to the seeds rather than every file: prose in the docs may legitimately name
# example.com, and this script says the string out loud two lines below.
if grep -rl -F '@example.com' src/main/resources/db 2>/dev/null | grep -q .; then
    echo "STILL PRESENT: @example.com in the seed migrations"
    grep -rl -F '@example.com' src/main/resources/db | sed 's/^/    /'
    leftovers=1
fi
for t in $TENANT_LIST; do
    grep -q "'$t'" src/main/resources/db/migration/V1_1_20__seed_tenants.sql \
        || { echo "MISSING: tenant '$t' in the registry seed"; leftovers=1; }
    grep -q "^      $t:$" src/main/resources/application-local.yaml \
        || { echo "MISSING: credentials for tenant '$t' in application-local.yaml"; leftovers=1; }
done

if [ "$leftovers" -ne 0 ]; then
    echo
    echo "Rename incomplete — fix the above before building."
    exit 1
fi

echo "Rename complete. Nothing names the template, and the values a service cannot start"
echo "without are filled in."
echo
echo "What the run decided for you, and where to change it:"
printf '  %-12s %-34s %s\n' product "$PRODUCT" "application.yaml, .env.example"
printf '  %-12s %-34s %s\n' tenants "$TENANT_LIST" "V1_1_20__seed_tenants.sql, application-local.yaml,"
printf '  %-12s %-34s %s\n' "" "" ".env.example, docker-compose.yml"
printf '  %-12s %-34s %s\n' CORS "https://*-$PRODUCT-local.qualtechedge.in" "application-local.yaml (servers: env var)"
printf '  %-12s %-34s %s\n' "seed mail" "<name>@$EMAIL_DOMAIN" "V1_1_0 seeds, system and tenant"
printf '  %-12s %-34s %s\n' "local db" "localhost:$DB_PORT" "application-local.yaml, .env.example"
echo
echo "Still yours, because nothing can decide them for you — see NEW-PROJECT.md:"
echo
echo "  1. Your product's tables      append at V1_0_30+ / V1_1_30+ — never renumber what exists"
if [ -n "$DROP_EXAMPLE" ]; then
    echo "  2. A first feature            the Example slice is gone; V1_0_30 and V1_1_30 are free"
else
    echo "  2. The Example slice          replace it with a real feature, or rerun with --drop-example"
fi
echo "  3. Deployment registration    QShip: never hand-set DB_* or FLYWAY_* — they are derived"
