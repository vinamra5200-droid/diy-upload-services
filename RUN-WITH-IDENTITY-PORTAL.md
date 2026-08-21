# Running a service from this template against identity-portal-service

A service built from this template can get its identity two ways, and the choice changes what a
"tenant" means here. Make it before seeding the tenant registry — the codes are the part that is
expensive to change later.

| | realm | what separates two tenants | `keycloak.platform-tenant` |
|---|---|---|---|
| **Its own Keycloak** | one per tenant | the issuer | blank |
| **identity-portal-service** | one per *platform tenant*, shared | the **audience** | set |

The second is the QCP direction: a person signs in once at the portal and opens every application
they hold, so the applications cannot each own an identity provider. `ibs-service` and
`lms-service` are both wired this way, and this document is the walk they follow.

## What the codes mean, and why they must match exactly

The portal names everything after a **scope code**, which is its hostname's prefix. A service's
own hostname names the application tenant and the product but not the organisation above them, so
the platform tenant is configuration here.

| Portal | This service |
|---|---|
| platform tenant `qc` | `spring.keycloak.platform-tenant: qc` |
| application `<product>` | `qcp.multitenancy.host.product: <product>` |
| application tenant `<apptenant>` | `<apptenant>` in `tenant.tenants` (`V1_1_20`) |
| realm `qc-identity-<env>` | `spring.keycloak.realm` |
| client `oauth-client-<apptenant>-<product>-qc` | **derived per host, never configured** |

**A tenant short code in the registry is an application tenant of the portal, spelled the portal's
way.** The audience this service demands is derived from it, so a code that differs by one
character makes the service demand a client that does not exist — on every request, for that
tenant only, with a 401 that names nothing.

The audience is derived per host rather than listed in `keycloak.accepted-audiences`, because every
application tenant of a platform tenant now shares one realm: signature and issuer no longer
separate them, and a flat list would accept one tenant's token on another tenant's host.

Seed **every** environment's codes in `V1_1_20`, not just the one you are standing up. The portal's
application tenants differ between local and dev, and a registry that changes shape per environment
is how a service works locally and 403s on a server.

## Portal side

### 1. Hostnames first — they are allocated, not derived

Infra creates hostnames in DNS ahead of time and they are registered as rows; the portal's dropdown
offers the ones still available and marks one used when it is claimed. Creating the application
fails with *"No available application domain exists for code '<product>'"* until a row exists, and
there is deliberately no endpoint that creates one — a hostname the backend invented would not
resolve.

Locally there is no infra, so stand in for it. In the **platform tenant's** database
(`qc-identity-local-db`), `identity` schema:

```sql
-- L3: the application's own host, claimed when the application is created
INSERT INTO identity.tenant_application_domains (application_code, application_url, status)
VALUES ('<product>', 'admin-<product>-qc-identity-local.qualtechedge.in', 1);

-- L4: the application-tenant host pattern, %s = application code. Check first — this pool is
-- shared across applications, so the code you want may already exist.
INSERT INTO identity.application_tenant_domain (code, url_format, status)
VALUES ('<apptenant>', '<apptenant>-%s-qc-identity-local.qualtechedge.in', 1);
```

`status` 1 = available, 2 = claimed.

### 2. Register the application, then the application tenant

As a tenant administrator on the platform tenant's host (`qc-identity-<env>.qualtechedge.in`):

```
POST /api/v1/application/applications
{"applicationCode":"<product>","applicationName":"...","isMultiTenant":true,
 "sessionTimeout":1800,"tokenExpiry":1800,"environment":"DEV", ...}
```

Then attach it to an application tenant. **Which verb depends on whether that tenant already
exists, and this is the step that surprises people:** an application tenant code can only be
*created* once — `POST` refuses a code that exists, and refuses again because its hostname pool row
is already claimed. Adding an application to a tenant that already runs another one is a **`PUT`**
with the *complete* application id list, existing ones included; the update path provisions the
client for the newly added applications only.

```
POST /api/v1/application/application-tenants   # a tenant code nothing uses yet
PUT  /api/v1/application/application-tenants/{id}   # a tenant that already runs something else
```

Either way this provisions the Keycloak client `oauth-client-<apptenant>-<product>-qc` with its
audience mapper, and stores the secret in Vault at
`cust-connect.ai/identity-portal-service/<env>/tenants/<apptenant>-<product>-qc`.

`ROLE_ADMIN` is required to map an application to a tenant while `ROLE_TENANT_ADMIN` can create the
application — so a tenant admin gets `403` mapping what it just created. Use an admin account for
this step.

### 3. Grant the application to the people who may open it

`PUT /api/v1/organization/users/{id}` with the application in `applicationIds` — the launcher shows
the subset a person holds, not everything the tenant owns.

### 4. Point the tile at this service's own host

The launcher reads `tenant_application_mappings.redirection_url`, and it is generated from the
portal's *own* hostname family. It has to be repointed at the application's host, by hand:

```sql
UPDATE identity.tenant_application_mappings
   SET redirection_url = '<apptenant>-<product>-<env>.qualtechedge.in'
 WHERE redirection_url = '<apptenant>-<product>-qc-identity-<env>.qualtechedge.in';
```

## Service side

```yaml
spring:
  keycloak:
    server-url: http://localhost:8081        # an address a BROWSER can reach
    realm: qc-identity-local
    platform-tenant: qc
qcp:
  multitenancy:
    host:
      product: <product>
```

On a server these are `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_PLATFORM_TENANT` and
`QCP_HOST_PRODUCT` — see `.env.example`.

At startup, one line proves the wiring:

```
SECURITY: building token validation for realm 'qc-identity-local'
          (issuer http://localhost:8081/realms/qc-identity-local), audience 'oauth-client-at1-<product>-qc'
```

**No audience in that line means `platform-tenant` did not bind**, and the service will then accept
any token from the realm on any host.

## Verify

Four requests, and the last two are the point:

```bash
curl -o /dev/null -w "own host      %{http_code}\n" http://localhost:<port>/api/v1/examples \
  -H "Host: <apptenant>-<product>-local.qualtechedge.in" -H "Authorization: Bearer $TOKEN"
curl -o /dev/null -w "no token      %{http_code}\n" http://localhost:<port>/api/v1/examples \
  -H "Host: <apptenant>-<product>-local.qualtechedge.in"
curl -o /dev/null -w "other tenant  %{http_code}\n" http://localhost:<port>/api/v1/examples \
  -H "Host: <other>-<product>-local.qualtechedge.in" -H "Authorization: Bearer $TOKEN"
curl -o /dev/null -w "other product %{http_code}\n" http://localhost:<port>/api/v1/examples \
  -H "Host: <apptenant>-<otherproduct>-local.qualtechedge.in" -H "Authorization: Bearer $TOKEN"
```

Expected `200 / 401 / 401 / 401`. Every one of those hosts resolves to the same realm, so the token
verifies on all four — only the audience refuses the last two. A run that returns 200 twice means
the audience is not being checked, whatever the startup log said.

Mint the token with the client the portal provisioned:

```bash
SECRET=$(docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=root ipl-vault \
  vault kv get -field=OAUTH2_CLIENT_SECRET \
  "cust-connect.ai/identity-portal-service/local/tenants/<apptenant>-<product>-qc")

# --data-urlencode, not -d: the generated secret contains characters that break form data.
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/qc-identity-local/protocol/openid-connect/token" \
  --data-urlencode "client_id=oauth-client-<apptenant>-<product>-qc" \
  --data-urlencode "client_secret=$SECRET" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=<user>" --data-urlencode "password=<password>" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
```

## Known gaps in the portal, as of August 2026

Each of these costs a debugging session the first time, and none of them is a fault in this service.

- **No just-in-time provisioning.** A portal grant lets Keycloak issue a valid token; it does not
  tell this service the person exists, so a completed sign-in is refused at the last step with a
  message that reads like a bad password. Seed the portal's users into `db/tenant` — in your own
  `V1_1_31+` range, not the template's — and delete that file when the portal provisions users
  itself. `ibs-service` learned this by inserting the row by hand on a live database.
- **The launcher tile URL is a hand-fix**, step 4 above.
- **The startup warning that `accepted-audiences` is empty is misleading** when `platform-tenant`
  is set: the static list is deliberately unused and the audience is derived per host instead.
- **A `-local` host cannot talk to a deployed service and a `-dev` host cannot talk to a local
  one.** The environment segment is part of the host grammar and is checked, deliberately.
