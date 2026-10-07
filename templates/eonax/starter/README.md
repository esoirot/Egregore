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
| 2 | Publish, catalog, negotiate, transfer (Dataspace Protocol) | `just publish`, `catalog`, `negotiate`, `transfer` | next |
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

## Kubernetes-ready rules

The stack runs on Docker Compose but must move to Kubernetes without redesign. Every component follows these rules (`templates/eonax/test` in Egregore checks the ones marked ✓):

1. ✓ One process per container; every component image is built from a `Dockerfile` (or pinned by digest), never from the dev container.
2. Configuration only from environment variables or mounted files (later: ConfigMaps).
3. Secrets only from a vault or environment variables (later: Kubernetes Secrets), never baked into images.
4. ✓ Components find each other by service name; no `network_mode`, no host paths outside the project.
5. ✓ Every component has a health endpoint and healthcheck (later: readiness and liveness probes).
6. Startup survives any start order (`restart: unless-stopped`; `depends_on` is only a convenience).
7. State only in Postgres or named volumes (later: persistent volume claims).

Images run as a non-root numeric user (10001), as Kubernetes `runAsNonRoot` expects.
