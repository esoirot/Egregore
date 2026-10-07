# gaiax-mock: a stand-in for the Gaia-X Digital Clearing House

**A mock, for learning offline. Never production.** Everything mock lives in this folder.

It plays, on the same paths and with the same payload shapes as the real services (versions pinned):

| Real service | Real base URL | Mock base URL |
| --- | --- | --- |
| GXDCH compliance v2.14.0 | `https://compliance.lab.gaia-x.eu/v2` | `http://gaiax-mock:8080/compliance/v2` |
| GXDCH registration number notary v2.10.1 | `https://registrationnumber.notary.lab.gaia-x.eu/v2` | `http://gaiax-mock:8080/notary/v2` |
| your web server (your `did.json`, your X.509 chain) | `https://<your domain>/...` | `http://gaiax-mock:8080/<path>/did.json` |

It checks what the real compliance service checks first: every JWT is signed by the key its issuer's DID document publishes, a `gx:LegalPerson` and a `gx:Issuer` (accepting the Gaia-X Terms and Conditions) are signed by the participant, the registration number is signed by a trusted notary. Its notary takes the real one's registration types (`vat-id`, `lei-code`, `eori`) and issues the same credential types (`dataspace/check-gaiax --lab` compares it with the real lab notary). It does **not**: ask the VAT/LEI/EORI registries, require an X.509 chain up to an eIDAS or EV trust anchor, or resolve DIDs it does not host.

What it signs is worth nothing outside: its keys are made at each start and its DIDs are `did:web:gaiax-mock...`, which no Gaia-X registry lists.

Files: `app.py` (the services), `mock.env` (the client settings used when no `GAIAX_*` is set), `publish-did` (puts your `did.json` on the mock), `Dockerfile`, `requirements.txt`.
