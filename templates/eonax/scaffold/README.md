# {{PROJECT_NAME}}

A mini data space shaped like EONA-X (the European mobility, transport and tourism data space), for learning it layer by layer. It runs the same building blocks: Eclipse Dataspace Components (EDC) connectors speaking the Dataspace Protocol, decentralized identities and credentials, and the Gaia-X Trust Framework. It is **not** EONA-X itself: their production connector, onboarding and rules are not public.

## Start here

From the project folder on the host, `dev up` (the first one builds every image: several minutes). Then, in `dev shell`:

```bash
just wait     # every runtime up
just test     # the whole data space, end to end, with every check (a few minutes)
just dev      # the trip planner on http://localhost:<APP_PORT> (APP_PORT in .env)
```

Then follow the chapters below one step at a time: each `just` step prints what happened, and its script in `dataspace/` (with the request bodies in `dataspace/requests/`) is the thing to read. To start over from nothing, see "After changing connector code" in chapter 1.

Template fixes reach this project with `dev update` (on the host, in the project folder), the course material too: `connector/`, `dataspace/`, `app/`, this README. A file you edited is kept, the template's new version lands next to it as `<file>.template-new`.

## The cast

| Participant | Role |
| --- | --- |
| `provider` (`did:web:provider-identityhub%3A7083:provider`) | e.g. a transport operator, publishes a public transport timetable (GTFS) |
| `consumer` (`did:web:consumer-identityhub%3A7083:consumer`) | e.g. a travel app, wants to use that timetable |
| authority (`did:web:issuerservice%3A10016:issuer`) | runs the data space: registers members, issues their MembershipCredential |

Each participant runs a **control plane** (catalog, contracts, transfers, its management API), a **data plane** (moves the data) and an **Identity Hub** (its DID, keys and credentials). The authority runs an **Issuer Service**. State lives in Postgres (one database per runtime), keys and secrets in Vault.

## Training path

| Chapter | Layer | Command | Status |
| --- | --- | --- | --- |
| 1 | Connector anatomy: control plane, data plane, health | `just health` | ready |
| 2 | Publish, catalog, negotiate, transfer (Dataspace Protocol) | `just publish`, `catalog`, `negotiate`, `transfer` | ready |
| 3 | Identity and trust: `did:web`, Identity Hub, Issuer Service, membership credential | `just authority`, `identity`, `membership` (all: `just onboard`) | ready |
| 4 | Usage policies (ODRL): purpose, time window | `just negotiate <asset> [purpose]`, `just transfer <asset>` | ready |
| 5 | Federated catalogue: search every member at once; any participant publishes | `just publish-pois`, `just federated-catalog` | ready |
| 6 | Gaia-X compliance (mocked unless configured) | `just gaiax` | ready |
| 7 | The consumer's app; the whole data space as one test | `just dev`, `just test` | ready |

## Chapter 1: connector anatomy

`dev up` starts the dev container (your workstation: Java, Gradle, curl, jq) and, next to it, the data space from `compose.template.yaml`:

| Service | Ports (inside the network) | What it does |
| --- | --- | --- |
| `provider-controlplane`, `consumer-controlplane` | 8080 `/api` (health), 8081 `/management` (your requests), 8082 `/protocol` (Dataspace Protocol, connector to connector), 8083 `/control` (to its data plane) | decides: what is offered, to whom, under which contract |
| `provider-dataplane`, `consumer-dataplane` | 8080 `/api` (health), 8083 `/control`, 8085 `/public` (data pulls) | moves the data once a contract allows it |
| `edc-postgres` | 5432 | state of every runtime |
| `provider-identityhub`, `consumer-identityhub` | 8080 `/api` (health), 7081 `/api/identity` (Identity API), 7082 `/api/credentials` (DCP: presents credentials), 7083 `/` (DID documents), 7084 `/api/sts` (tokens for its connector) | a participant's identity and wallet |
| `issuerservice` | 8080 `/api` (health), 10012 `/api/issuance` (DCP issuance), 10013 `/api/admin` (members, credential definitions), 10015 `/api/identity`, 10016 `/` (DID document), 9999 `/statuslist` | the authority |
| `vault` | 8200 | secrets: keys and tokens, one folder per participant; data in a named volume |
| `vault-seed` | — | one-shot job at each `dev up`: initializes (first time) and unseals Vault, puts the encryption and transfer token keys in it |
| `gtfs-backend` | 8000 | the provider's own system holding the timetable; not part of the data space |
| `poi-backend` | 8000 | the consumer's own system holding its tourist points of interest (it offers them too) |
| `gaiax-mock` | 8080 | **mock** of the Gaia-X Digital Clearing House, only with the profile `gaiax-mock` (`gaiax-mock/README.md`) |

From `dev shell`:

```bash
just health                                   # every component ready?
curl -s http://provider-controlplane:8080/api/check/readiness | jq
```

Things to notice:

* A data plane registers itself with its control plane at start (`EDC_DPF_SELECTOR_URL`): the control plane then knows where to send transfers.
* The connector code is only a list of EDC modules (`connector/controlplane/build.gradle.kts`, `connector/dataplane/build.gradle.kts`): EDC loads every extension it finds on the classpath. The control plane uses `iam-mock` for now: every participant is trusted until the trust chapter replaces it with credentials.
* All configuration is environment variables in `compose.template.yaml`: `EDC_PARTICIPANT_ID` is the setting `edc.participant.id`, and so on.

After changing connector code, rebuild the images from the project folder on the host: `docker compose build && dev down && dev up` (not `docker compose up`: it recreates `dev` without what `dev exec` needs).

Vault keeps its unseal key in the `vault-init` volume, next to its data: fine to learn, never in production (use auto-unseal). To start the data space over from nothing (identities, credentials, offers): `dev down`, then `docker volume rm <project>_vault-data <project>_vault-init <project>_edc-postgres-data`, then `dev up` and `just onboard`. Always the three together: Postgres records without their Vault keys (or the reverse) leave broken identities.

## Chapter 2: publish, catalog, negotiate, transfer

The heart of a data space: a provider offers data under a contract, a consumer finds it, agrees to the contract, and gets the data, without ever learning where the provider keeps it. Since chapter 3 every request needs a membership: run `just onboard` first (once), then the steps one by one (each prints what happened), or everything with `just flow`.

| Step | Who | What happens | Read |
| --- | --- | --- | --- |
| `just publish` | provider | creates an **asset** (the GTFS feed and its real location, `dataAddress`), a **policy** (`members-only`, see chapter 3) and a **contract definition** (this asset, under this policy) in its control plane | `dataspace/publish`, `dataspace/requests/asset.json`, `policy.json`, `contract-definition.json` |
| `just catalog` | consumer | asks the provider's connector for its **catalog** (Dataspace Protocol); each dataset carries **offers** (`odrl:hasPolicy`) | `dataspace/catalog`, `.dataspace/catalog.json` |
| `just negotiate` | consumer | sends the offer back **exactly as offered** (rules included) as a contract request; both connectors run the negotiation until **FINALIZED**, giving a **contract agreement** | `dataspace/negotiate`, `requests/contract-request.json`, `.dataspace/offer.<asset>.json` |
| `just transfer` | consumer | starts a **transfer process** (HTTP pull) under the agreement; the provider's data plane hands out an **EDR** (endpoint + short-lived token); the consumer pulls `downloads/gtfs-demo-transit.zip` with it | `dataspace/transfer`, `.dataspace/edr.<asset>.json` |

Things to notice:

* You always talk to **your own** connector (its management API, with your `X-Api-Key`); connectors talk to each other over the Dataspace Protocol (`/protocol`).
* The consumer never sees `http://gtfs-backend:8000`: the EDR points at the provider's data plane, which fetches from the backend.
* The EDR token is signed with keys from Vault (`vault-seed` put them there); try the same `curl` later and it expires.
`negotiate` and `transfer` take an asset (default `gtfs-demo-transit`; others in chapter 4). `just test` runs the whole flow, checks the consumer received a GTFS feed, then checks the trust rules (chapter 3) and the usage policies (chapter 4). Management API keys default to `provider-api-key` and `consumer-api-key`; set `PROVIDER_API_KEY` and `CONSUMER_API_KEY` in `.env` to change them (then `dev down && dev up`).

## Chapter 3: identity and trust

Until now anyone could talk to the provider. A real data space only lets its **members** in, and nobody takes anybody's word for it: the authority **issues** a signed credential, each participant keeps it in its own **wallet** and **presents** it, and the other side **verifies** the signature against the authority's public key. No central server is asked at request time. This is the Decentralized Claims Protocol (DCP), on W3C DIDs and Verifiable Credentials.

| Step | Who | What happens | Read |
| --- | --- | --- | --- |
| `just authority` | authority | its identity (`did:web:issuerservice%3A10016:issuer`, signing key, issuance endpoint), how it checks a member (the `membership` **attestation**, our Java code in `connector/issuerservice/`), what it issues (the **MembershipCredential definition**), who the members are (provider and consumer, registered as **holders**) | `dataspace/authority`, `requests/authority.json`, `membership-*.json`, `holder.json` |
| `just identity provider` (and `consumer`) | participant | its Identity Hub creates its **DID document** (`curl http://provider-identityhub:7083/provider/did.json`), its key pair (private key in its Vault folder), and an STS client for its connector | `dataspace/identity`, `requests/participant-context.json` |
| `just membership provider` (and `consumer`) | participant | its Identity Hub asks the authority for a MembershipCredential (DCP issuance); the authority checks the request comes from a registered holder, signs, and delivers it to the wallet | `dataspace/membership`, `requests/credential-request.json` |

How a request is trusted now (`just catalog`, `negotiate`, `transfer`):

1. The consumer's control plane gets a token from its own Identity Hub (STS), allowed to read its MembershipCredential (the `membership` scope, `EDC_IAM_DCP_SCOPES_MEMBERSHIP_*`).
2. The provider resolves the consumer's DID, verifies the token, and asks the consumer's Identity Hub for a presentation of that credential.
3. It checks the credential is signed by a **trusted issuer** (`EDC_IAM_TRUSTED-ISSUER_AUTHORITY_ID`): no valid membership, no answer (HTTP 401).
4. Then policies run on the credentials' claims: `members-only` holds when the **CEL expression** `membership-cel` is true (`requests/cel-membership.json`: a MembershipCredential that has started). CEL expressions are data the provider publishes, not code.

Things to try:

* `curl -s http://issuerservice:10016/issuer/did.json | jq` and the participants' DID documents: keys and service endpoints, all public.
* The partners-only offer (`requests/policy-partners.json`) needs a PartnerCredential nobody holds: it never shows in the consumer's catalog.
* Remove the consumer's credential (what `dataspace/check-trust` does), and every request is refused; `just membership consumer` brings it back.

The authority's attestation accepts every registered holder: registering a holder is the membership decision. A real authority would check its own registry there.

## Chapter 4: usage policies

Chapter 3 decided **who** gets in. Usage policies decide **how** the data may be used once you have it. They are ODRL constraints in the contract policy, which the consumer accepts by signing (`.dataspace/offer.<asset>.json` shows them; `just catalog` prints them):

| Rule | ODRL constraint | When it is checked | Read |
| --- | --- | --- | --- |
| members only | `edc:MembershipCredential eq active` | every request (CEL `membership-cel`) | `requests/policy-gtfs-contract.json` |
| purpose | `odrl:purpose eq mobility` | signing and use; the CEL expression `purpose-cel` limits purposes to the data space's (`mobility`, `tourism`) | `requests/cel-purpose.json` |
| time window | `edc:inForceDate lteq contractAgreement+1d` | at use (transfer), then by the **policy monitor** while the transfer runs | EDC built-in |

Try them:

* `just negotiate gtfs-demo-transit advertising`: the consumer asks for another purpose. The provider's terms are not negotiable: it answers with its own agreement (mobility), which no longer matches the consumer's request, so the consumer rejects it and the negotiation ends TERMINATED. Purpose is a legal promise the consumer signs: nothing technical stops it from misusing the data afterwards, which is why the agreement is recorded on both sides (audit).
* `just negotiate gtfs-expired`, then `just transfer gtfs-expired`: the contract window ended on 2026-01-01. Signing works (time is not checked at signing), the transfer is refused: the rule is checked when you **use** the contract.
* `just negotiate gtfs-30s`, then `just transfer gtfs-30s`: valid for 30 s after signing. The transfer starts; within about 10 s after the end, the provider's **policy monitor** (`EDC_POLICY_MONITOR_PERIOD`, here every 10 s, EDC's default is 1 hour) terminates it, and the EDR token you got stops working (HTTP 403): enforcement is technical here.

`dataspace/check-policies` runs these three checks (part of `just test`).

## Chapter 5: federated catalogue

`just catalog` asks one participant. In a data space of hundreds you search everyone at once: each control plane runs a **federated catalog crawler** that collects the members' catalogs into a local cache (every 15 s here), and you query the cache.

| Step | Who | What happens | Read |
| --- | --- | --- | --- |
| `just publish-pois` | consumer | any participant can provide: the travel app offers its tourist points of interest (GeoJSON, from its own `poi-backend`), members only, from its own control plane | `dataspace/publish-pois`, `requests/asset-pois.json`, `contract-definition-pois.json` |
| `just federated-catalog` | consumer | queries its crawled cache: every member's offers in one answer (`/management/v3/catalogs/request`) | `dataspace/federated-catalog`, `.dataspace/federated-catalog.json` |

Who gets crawled: the members' **DIDs** (`EONAX_CATALOG_PARTICIPANTS`). Our `MemberDirectoryExtension` (`connector/controlplane/src/`) resolves each DID at every crawl and takes its `ProtocolEndpoint` from the DID document (set by `just identity`): no address is configured anywhere, and a member that joins later shows up by itself. A real data space would read the member list from a registry instead of a setting.

The crawler asks each member like any consumer would, over DCP: it only sees what its credentials allow (`gtfs-partners-only` stays hidden here too).

## Chapter 6: Gaia-X compliance

Membership says "the authority knows you". Gaia-X compliance says more: your **legal identity** (a company, its registration number) is verified, you **accept the Gaia-X Terms and Conditions**, and a Gaia-X Digital Clearing House (GXDCH) signs that you comply. Data spaces like EONA-X build on that trust framework.

`just gaiax` runs the Gaia-X Loire flow for the provider (client: `connector/gaiax/`, all credentials are VC-JWT signed with ES256):

1. writes the provider's Gaia-X DID document (key, X.509 chain URL) and publishes it (on your domain; in mock mode, on the mock)
2. asks the **notary** to sign the registration number (`GAIAX_REGISTRATION_NUMBER`, `<type>:<number>`): `vat-id` gives a `gx:VatID`, `lei-code` a `gx:LeiCode`, `eori` a `gx:EORI` (the mock uses an LEI)
3. signs a `gx:LegalPerson` (name, address, registration number) and a `gx:Issuer` (accepts the Terms and Conditions)
4. presents the three to the **compliance service** (a VP-JWT); it checks them and returns a `gx:LabelCredential`
5. stores that credential in the provider's wallet (its Identity Hub)

Every step leaves its credential in `.dataspace/gaiax/` (decode one: `cut -d. -f2 .dataspace/gaiax/compliance.jwt | base64 -d 2>/dev/null | jq`).

**Mock or real.** Without any `GAIAX_*` variable, the client talks to `gaiax-mock` (the `gaiax-mock` profile in `.env` starts it): offline, but what it signs is trusted by nobody (`gaiax-mock/README.md` says what it checks and what not). For the real GXDCH you need a domain serving your `did.json` over HTTPS, an X.509 certificate chain for your key, a real registration number, and every `GAIAX_*` variable listed in `.env` (a partial set is refused); remove `gaiax-mock` from `COMPOSE_PROFILES`, then `dev down && dev up`. The client code does not change. `just health` says which mode is on.

How close the mock is to the real thing: `dataspace/check-gaiax --lab` (needs the internet) asks the real GXDCH **lab notary** for public registration numbers of each type and checks it answers like the mock (issuer, credential type, subject). The real **compliance** service was never run end to end from this project: it needs a public HTTPS domain for your `did.json` and an X.509 chain for your signing key up to a trust anchor. A VAT ID goes through the EU VIES registry, which often rate-limits the notary: an LEI is the more reliable choice.

## Chapter 7: the consumer's app

What it was all for: the travel app shows its users a trip planner (`just dev`, then http://localhost:<APP_PORT>). The bus line and its timetable come from the provider **through the data space**, under the contract of chapter 4; the places to visit are the travel app's own data. If there was never a transfer, `just dev` runs `just flow` first.

The page (`app/index.html`, plain JavaScript drawing SVG, no external library) is served by `app/Server.java`, a small server on Java's built-in HTTP server (`dataspace/consumer-app` starts it). Each time the page loads, the server pulls the timetable live: it asks the consumer's control plane for the running transfer's EDR, then calls the provider's data plane with the EDR's token, as `just transfer` does. Nothing is read from `downloads/`. If the transfer is no longer running (stopped, or the contract has ended), the server starts a new one (`just transfer`) once. Each answer's `X-Transfer-Process` header names the transfer it used.

`just test` runs everything, in order: `wait`, the flow (onboard, publish, catalog, negotiate, transfer), the trust checks, the usage policies, Gaia-X, the federated catalog, and the app (`consumer-app --check`: live data, and a new transfer once the old one is stopped).

## Kubernetes-ready rules

The stack runs on Docker Compose but must move to Kubernetes without redesign. Every component follows these rules (`templates/eonax/test` in Egregore checks the ones marked ✓):

1. ✓ One process per container; every component image is built from a `Dockerfile` (or pinned by digest), never from the dev container.
2. Configuration only from environment variables or mounted files (later: ConfigMaps).
3. Secrets only from a vault or environment variables (later: Kubernetes Secrets), never baked into images.
4. ✓ Components find each other by service name; no `network_mode`, no host paths outside the project.
5. ✓ Every component has a health endpoint and healthcheck (later: readiness and liveness probes); one-shot jobs (label `eonax.role: job`, like `vault-seed`) run once instead (later: Kubernetes Jobs).
6. Startup survives any start order (`restart: unless-stopped`; `depends_on` is only a convenience).
7. State only in Postgres or named volumes (later: persistent volume claims). Postgres keeps the identities' records and Vault their private keys: both persist, or identities break after a restart.

Images run as a non-root numeric user (10001), as Kubernetes `runAsNonRoot` expects.
