-- One database per runtime: each component owns its state (later: one database or schema per Kubernetes workload).
CREATE DATABASE provider_controlplane;
CREATE DATABASE provider_dataplane;
CREATE DATABASE consumer_controlplane;
CREATE DATABASE consumer_dataplane;
CREATE DATABASE provider_identityhub;
CREATE DATABASE consumer_identityhub;
CREATE DATABASE issuerservice;
