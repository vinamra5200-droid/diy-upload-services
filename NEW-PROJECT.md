# Starting a new project from this template

Clone, rename, build. The rename is a script and it fills in everything mechanical; what is
left are two decisions nothing can make for you.

```bash
git clone <this repo> billing-service && cd billing-service
rm -rf .git && git init                      # start your own history

script/rename-project.sh --package in.qualtechedge.billing \
                        --artifact billing-service \
                        --db-prefix billing
```

That is enough to `mvn package` and start: the service comes up, provisions its tenant
database and answers on `qc-billing-local.qualtechedge.in`. Add what you already know and it
comes up the way you want it instead:

```bash
script/rename-project.sh --package in.qualtechedge.billing \
                        --artifact billing-service \
                        --db-prefix billing \
                        --tenants qc,at1 \
                        --db-port 5433 \
                        --platform-tenant qc --realm qc-identity-local \
                        --drop-example
```

Run it **before writing any code**. It rewrites every source file, so doing it later means
rewriting your own work along with the template's. Add `--dry-run` first if you want to see
what it will touch. It refuses to finish if anything still names the template, if the product
segment is still blank, or if a tenant is seeded without credentials — a rename that half
worked is worse than one that failed, because it compiles and only surfaces months later as a
database named after the template.

---

## What the rename fills in

These used to be hand-edits. They are listed here because each one still has a reason worth
knowing when you come to change it.

**The product segment**, `qcp.multitenancy.host.product`. There is no default in the template
on purpose — a default would put the template's own name in the host grammar of every project
cloned from it — and a blank one matches no host at all, so the script writes it from
`--product` or from the artifact name.

**Tenant short codes**, in the four places that must agree, because two of them are the
*credentials* for the other two:

| File | What it holds |
|---|---|
| `db/migration/V1_1_20__seed_tenants.sql` | the registry rows and each tenant's `db_url` |
| `application-local.yaml` | `qcp.multitenancy.tenants.<code>.db-username/password` |
| `.env.example` / `docker-compose.yml` | `QCP_MULTITENANCY_TENANTS_<CODE>_DB*` for server profiles |

A tenant seeded in the registry with no credentials entry fails at provisioning, not at
startup, so the first sign of it is one tenant working and another not.

Under identity-portal-service these codes are not yours to invent: they are the portal's
application tenant codes, and seeding every environment's codes — not only the one you are
standing up first — is what stops a service working locally and 403-ing on a server.

Tenant credentials are **role names the service creates**, not existing logins, and the script
prefixes them with the product for a reason: several QCP services share one local PostgreSQL
and they share tenant codes too, so a bare `qc_user` is the same role in all of them — and each
service's provisioner refreshes that role's password at boot, quietly breaking whichever
service started first. Never reuse the service's own database user either: the provisioner
would reset the password of the role it is connected as, mid-boot.

**Seed addresses.** `email_id` and `mobile_number` are `UNIQUE`, so the `example.com`
placeholders are not merely untidy. `password` is `NULL` in both seeds and should stay `NULL`:
identity lives in Keycloak, and a hash in the table is a second credential that nothing rotates
and that still works after the realm account is disabled.

**CORS origin patterns**, `qcp.security.cors.allowed-origin-patterns`. Patterns, not hosts —
under the subdomain convention one line covers every tenant, `https://*-billing-local…` for the
local profile and an environment variable on the servers. Get this wrong and sign-in fails in
the browser with a bare CORS error and nothing useful on the server; the effective list is
logged at INFO on startup for exactly that reason.

---

## The decisions that are still yours

### 1. Where identity comes from

Two answers, and this one decides what a tenant short code *is* —
see [`RUN-WITH-IDENTITY-PORTAL.md`](RUN-WITH-IDENTITY-PORTAL.md) for the full walk.

**identity-portal-service issues the tokens** (the QCP direction — `ibs-service`, `lms-service`).
Set `spring.keycloak.platform-tenant` and the realm the portal provisioned. One realm then serves
every application tenant of that platform tenant, so the issuer no longer separates them and the
**audience** does; setting the platform tenant is what switches that check on. The client id is
derived per host — `oauth-client-<apptenant>-<product>-<platform-tenant>` — never configured, so
your registry's short codes must be the portal's application tenant codes spelled exactly its way.
`backend-client-id-format` is not used in this mode.

**A Keycloak of this service's own**, one realm per tenant. Leave `platform-tenant` blank and set
`spring.keycloak.backend-client-id-format` — `%s` is the tenant short code.

Client secrets come from Vault on a server; for local development put them in
`spring.keycloak.client-secrets.<code>`. There is deliberately no built-in fallback secret: one
that works is one nobody ever changes.

### 5. Replace the Example slice

`ExampleController/Service/ServiceImpl/Entity/Mapper/Repository/Documentation`, their DTOs and
tests, plus `db/tenant/V1_0_30__create_example_entity.sql` and `V1_1_30__seed_example_entity.sql`.
It exists to show the shape of a tenant-scoped CRUD path end to end. Delete it once yours works.

### 6. Your own migrations — append, never renumber

See below. This is the one that costs weeks if it goes wrong.

---

## Migration layout

Two sets, two databases, two Flyway histories that never see each other:

- **`db/migration`** — the *system* (admin) database. Tenant registry, console users, platform
  config. Migrated by Spring at startup. Schemas: `auth`, `image`, `tenant`.
- **`db/tenant`** — every *tenant* database, applied per tenant by `TenantProvisioningService`.
  A single `public` schema: a tenant database holds one tenant, so a sub-schema or a `tenant_`
  prefix only restates what the database already is.

Both use reserved number blocks:

| Block | Owner | Contents |
|---|---|---|
| `V1_0_0` | template | schemas |
| `V1_0_1` – `V1_0_19` | template | identity core — users, roles, API clients, images |
| `V1_0_20` – `V1_0_29` | template | tenancy registry |
| **`V1_0_30`+** | **you** | your product's tables |
| `V1_1_0` – `V1_1_19` | template | identity seeds |
| `V1_1_20` – `V1_1_29` | template | tenancy seeds |
| **`V1_1_30`+** | **you** | your product's seeds |

**The gaps are the point.** They exist so that pulling a later version of this template adds
files in the template's range and never collides with yours, and so you never have to renumber.

### The rule that matters

**An applied migration is immutable — including its comments.** Flyway checksums the whole file.
Editing an applied migration fails validation with `checksum mismatch for migration version
1.1.0`, and then *no* migration runs: the service does not start. Correcting a misleading
comment in an applied file has broken every tenant of a live service. A later migration is the
only place a change of mind can be recorded.

Two consequences worth knowing before you hit them:

- **Renumbering means dropping the database**, not migrating it. Flyway cannot reconcile
  versions that moved. This is survivable in development and expensive later, which is why the
  reserved ranges exist.
- **`flyway repair` is not the shortcut it appears to be.** It rewrites the recorded checksums
  and nothing else, so an edited migration is marked applied while its new content — the added
  index, the corrected seed — never runs. The database then disagrees with the scripts and
  nothing reports it.

---

## Deploying through the deployment console

Registering a service is quick; four things about it are not obvious and each has cost real
time on a previous project.

**Never hand-set `DB_*` or `FLYWAY_*` environment variables.** The console derives
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` and `DB_URL` itself. Operator
variables are written *after* the derived ones, so a hand-set duplicate silently wins and points
Flyway at the wrong database.

**Know whether a value is needed at build time or run time.** A value compiled into a front-end
bundle needs `buildArg: true`; a value read by an entrypoint at container start is an ordinary
environment variable. Setting the wrong one fails silently — deploy succeeds, container healthy,
value never arrives.

**A service at a path prefix needs `routeStripPrefix: true`.** Without it the backend receives
the prefix it does not expect and every call 404s. Irrelevant for a service at `/`.

**`resetDatabase` covers only the application's own database.** Tenant databases are provisioned
by the service, keep their own history under `db/tenant`, and are untouched by the flag. If a
tenant migration changed, drop them by hand first — otherwise the admin database migrates, and
each tenant then fails validation on its own history.

**A SUCCESS run is not proof the service works.** Check three things: the run, that the container
is up *and* the edge is actually bound (`ss -ltnp`), and that the service answers through the
edge.

---

## Verifying a migration change without a server

No credentials and no dev server needed — a throwaway Postgres and the Flyway image:

```bash
docker network create tmpl-net
docker run -d --name tmpl-pg --network tmpl-net \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=sys postgres:17.6

# the system database
docker run --rm --network tmpl-net -v "$PWD/src/main/resources/db/migration:/flyway/sql:ro" \
  flyway/flyway:11 -url=jdbc:postgresql://tmpl-pg:5432/sys \
  -user=postgres -password=postgres -locations=filesystem:/flyway/sql \
  -placeholders.db_host=tmpl-pg -placeholders.db_port=5432 migrate

# a tenant database
docker exec tmpl-pg psql -U postgres -d postgres -c 'CREATE DATABASE tenant1;'
docker run --rm --network tmpl-net -v "$PWD/src/main/resources/db/tenant:/flyway/sql:ro" \
  flyway/flyway:11 -url=jdbc:postgresql://tmpl-pg:5432/tenant1 \
  -user=postgres -password=postgres -locations=filesystem:/flyway/sql \
  -placeholders.tenant_code=qc migrate

docker rm -f tmpl-pg && docker network rm tmpl-net
```

The placeholders are required: without them the run stops at the first file that uses one.
`db_host`/`db_port` for the system set, `tenant_code` for the tenant set.

---

## What the template already handles

Worth knowing so you do not rebuild it:

- **Tenant resolution from the host.** `HostUtils` parses `{tenant}-{product}-{env}.{domain}`
  structurally — no domain is hardcoded. `admin-*` resolves to no tenant and runs in system
  scope.
- **A database per tenant**, provisioned at runtime: role, database, ownership and migrations.
  The system database's user needs `CREATEDB` and `CREATEROLE`.
- **One `EntityManagerFactory` over both**, which is why `spring.jpa.hibernate.ddl-auto` must
  stay `none` — Flyway owns both schemas and Hibernate cannot validate against one database.
- **Both identity models**: a Keycloak of this service's own with a realm per tenant, or
  identity-portal-service with one shared realm and the audience derived per host. Which one you
  get depends on a single property — see [`RUN-WITH-IDENTITY-PORTAL.md`](RUN-WITH-IDENTITY-PORTAL.md).
- **Vault for per-tenant secrets**, and the service starts without one.
