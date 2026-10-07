# {{PROJECT_NAME}}

A mini data space shaped like EONA-X (the European mobility, transport and tourism data space), for learning it layer by layer. It runs the same building blocks: Eclipse Dataspace Components (EDC) connectors speaking the Dataspace Protocol, decentralized identities and credentials, and the Gaia-X Trust Framework. It is **not** EONA-X itself: their production connector, onboarding and rules are not public.

## The cast

| Participant | Role |
| --- | --- |
| `provider` | e.g. a transport operator, publishes a public transport timetable (GTFS) |
| `consumer` | e.g. a travel app, wants to use that timetable |
| authority | runs the data space: issues membership credentials (trust chapter) |

Each participant runs a **control plane** (catalog, contracts, transfers, its management API) and a **data plane** (moves the data). State lives in Postgres, one database per runtime.

## Training path

| Chapter | Layer | Command | Status |
| --- | --- | --- | --- |
| 1 | Connector anatomy: control plane, data plane, health | `just health` | ready |
| 2 | Publish, catalog, negotiate, transfer (Dataspace Protocol) | `just publish`, `catalog`, `negotiate`, `transfer` (all: `just flow`) | ready |
| 3 | Identity and trust: `did:web`, Identity Hub, Issuer Service, membership credential | `just onboard` | planned |
| 4 | Usage policies (ODRL): purpose, time window | `just negotiate` | planned |
| 5 | Federated catalogue | `just catalog` | planned |
| 6 | Gaia-X compliance (mocked unless configured) | `just gaiax` | planned |
| 7 | Consumer app, full flow test | `just dev`, `just test` | planned |

## Chapter 1: connector anatomy

`dev up` starts the dev container (your workstation: Java, Gradle, curl, jq) and, next to it, the data space from `compose.template.yaml`:

| Service | Ports (inside the network) | What it does |
| --- | --- | --- |
| `provider-controlplane`, `consumer-controlplane` | 8080 `/api` (health), 8081 `/management` (your requests), 8082 `/protocol` (Dataspace Protocol, connector to connector), 8083 `/control` (to its data plane) | decides: what is offered, to whom, under which contract |
| `provider-dataplane`, `consumer-dataplane` | 8080 `/api` (health), 8083 `/control`, 8085 `/public` (data pulls) | moves the data once a contract allows it |
| `edc-postgres` | 5432 | state of every runtime |
| `vault` | 8200 | secrets: each participant's keys, in its own folder (dev mode: in memory) |
| `vault-seed` | — | one-shot job at each `dev up`: puts the transfer token keys into Vault |
| `gtfs-backend` | 8000 | the provider's own system holding the timetable; not part of the data space |

From `dev shell`:

```bash
just health                                   # every component ready?
curl -s http://provider-controlplane:8080/api/check/health | jq
```

Things to notice:

* A data plane registers itself with its control plane at start (`EDC_DPF_SELECTOR_URL`): the control plane then knows where to send transfers.
* The connector code is only a list of EDC modules (`connector/controlplane/build.gradle.kts`, `connector/dataplane/build.gradle.kts`): EDC loads every extension it finds on the classpath. The control plane uses `iam-mock` for now: every participant is trusted until the trust chapter replaces it with credentials.
* All configuration is environment variables in `compose.template.yaml`: `EDC_PARTICIPANT_ID` is the setting `edc.participant.id`, and so on.

After changing connector code, rebuild the images from the project folder on the host: `docker compose build && dev down && dev up`.

## Chapter 2: publish, catalog, negotiate, transfer

The heart of a data space: a provider offers data under a contract, a consumer finds it, agrees to the contract, and gets the data, without ever learning where the provider keeps it. Run the steps one by one (each prints what happened), or all of them with `just flow`.

| Step | Who | What happens | Read |
| --- | --- | --- | --- |
| `just publish` | provider | creates an **asset** (the GTFS feed and its real location, `dataAddress`), a **policy** (here: no rules) and a **contract definition** (this asset, under this policy) in its control plane | `dataspace/publish`, `dataspace/requests/asset.json`, `policy.json`, `contract-definition.json` |
| `just catalog` | consumer | asks the provider's connector for its **catalog** (Dataspace Protocol); each dataset carries **offers** (`odrl:hasPolicy`) | `dataspace/catalog`, `.dataspace/catalog.json` |
| `just negotiate` | consumer | requests a contract on the offer; both connectors run the negotiation until **FINALIZED**, giving a **contract agreement** | `dataspace/negotiate`, `requests/contract-request.json` |
| `just transfer` | consumer | starts a **transfer process** (HTTP pull) under the agreement; the provider's data plane hands out an **EDR** (endpoint + short-lived token); the consumer pulls `downloads/gtfs.zip` with it | `dataspace/transfer`, `.dataspace/edr.json` |

Things to notice:

* You always talk to **your own** connector (its management API, with your `X-Api-Key`); connectors talk to each other over the Dataspace Protocol (`/protocol`).
* The consumer never sees `http://gtfs-backend:8000`: the EDR points at the provider's data plane, which fetches from the backend.
* The EDR token is signed with keys from Vault (`vault-seed` put them there); try the same `curl` later and it expires.
* Identity is still mocked: the provider trusts anyone. Chapter 3 replaces that with credentials.

`just test` runs the whole flow and checks the consumer received a GTFS feed. Management API keys default to `provider-api-key` and `consumer-api-key`; set `PROVIDER_API_KEY` and `CONSUMER_API_KEY` in `.env` to change them (then `dev down && dev up`).

## Kubernetes-ready rules

The stack runs on Docker Compose but must move to Kubernetes without redesign. Every component follows these rules (`templates/eonax/test` in Egregore checks the ones marked ✓):

1. ✓ One process per container; every component image is built from a `Dockerfile` (or pinned by digest), never from the dev container.
2. Configuration only from environment variables or mounted files (later: ConfigMaps).
3. Secrets only from a vault or environment variables (later: Kubernetes Secrets), never baked into images.
4. ✓ Components find each other by service name; no `network_mode`, no host paths outside the project.
5. ✓ Every component has a health endpoint and healthcheck (later: readiness and liveness probes); one-shot jobs (label `eonax.role: job`, like `vault-seed`) run once instead (later: Kubernetes Jobs).
6. Startup survives any start order (`restart: unless-stopped`; `depends_on` is only a convenience).
7. State only in Postgres or named volumes (later: persistent volume claims).

Images run as a non-root numeric user (10001), as Kubernetes `runAsNonRoot` expects.
