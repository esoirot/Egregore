"""MOCK of the Gaia-X Digital Clearing House (GXDCH), for learning offline. Never production.

Plays, with the same paths and payload shapes as the real services it imitates:
  - the registration number notary (GXDCH notary v2.10.1):
      GET  /notary/v2/registration-numbers/vat-id/{vatId}?vcId=&subjectId=   -> gx:VatID VC-JWT
  - the compliance service (GXDCH compliance v2.14.0):
      POST /compliance/v2/api/credential-offers/standard-compliance?vcid=      (application/vp+jwt)
                                                                              -> gx:LabelCredential VC-JWT
  - the public web server of your domain (where your did.json would live):
      PUT/GET /<path>/did.json, GET /x509CertificateChain.pem

What it checks (like the real compliance service): the presentation and every credential are signed
by the keys their issuers' DID documents publish; a gx:LegalPerson and a gx:Issuer (accepting the
Gaia-X Terms and Conditions) signed by the participant; a registration number signed by a trusted
notary. What it does not: the notary does not ask the VIES/LEI registries, signatures need no X.509
chain up to an eIDAS/EV trust anchor, and only DIDs it hosts itself resolve. Its keys are made at
start, its DIDs are did:web:gaiax-mock...: no real Gaia-X registry trusts what it signs.
"""

import base64
import hashlib
import json
import os
import re
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature, encode_dss_signature
from cryptography.x509.oid import NameOID

HOST = os.environ.get("MOCK_HOST", "gaiax-mock:8080")
DID_HOST = HOST.replace(":", "%3A")
NOTARY_DID = f"did:web:{DID_HOST}:notary:v2"
COMPLIANCE_DID = f"did:web:{DID_HOST}:compliance:v2"
GX_CONTEXT = "https://w3id.org/gaia-x/development#"
VC_CONTEXT = "https://www.w3.org/ns/credentials/v2"
# SHA-256 of the Gaia-X Terms and Conditions a participant accepts (gx:Issuer), as GXDCH expects it.
TERMS_AND_CONDITIONS = "4bd7554097444c960292b4726c2efa1373485e8a5565d94d41195214c5e0ceb3"


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def unb64(text):
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def public_jwk(key):
    numbers = key.public_key().public_numbers()
    return {"kty": "EC", "crv": "P-256", "x": b64(numbers.x.to_bytes(32, "big")), "y": b64(numbers.y.to_bytes(32, "big"))}


def did_document(did, key):
    method = f"{did}#key-0"
    return {
        "@context": ["https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1"],
        "id": did,
        "verificationMethod": [{"id": method, "type": "JsonWebKey2020", "controller": did, "publicKeyJwk": public_jwk(key)}],
        "assertionMethod": [method],
    }


def sign(payload, key, did, typ):
    header = {"alg": "ES256", "typ": f"{typ}+jwt", "cty": typ, "iss": did, "kid": f"{did}#key-0"}
    signing_input = f"{b64(json.dumps(header).encode())}.{b64(json.dumps(payload).encode())}"
    r, s = decode_dss_signature(key.sign(signing_input.encode(), ec.ECDSA(hashes.SHA256())))
    return f"{signing_input}.{b64(r.to_bytes(32, 'big') + s.to_bytes(32, 'big'))}"


class Rejected(Exception):
    pass


def resolve(did):
    """did:web:<host>:<path> -> the document this mock hosts at /<path>/did.json (no others)."""
    match = re.fullmatch(r"did:web:([^:]+)((?::[^:#]+)*)", did)
    if not match or unquote(match.group(1)) != HOST:
        raise Rejected(f"cannot resolve {did}: the mock only resolves the DIDs it hosts (did:web:{DID_HOST}:...)")
    path = "/".join(match.group(2).split(":")) + "/did.json"
    if path not in documents:
        raise Rejected(f"cannot resolve {did}: nothing published at http://{HOST}{path}")
    return documents[path]


def verify(token):
    """Checks a JWT's ES256 signature against its issuer's DID document; returns (issuer, payload)."""
    try:
        header_b64, payload_b64, signature_b64 = token.split(".")
        header = json.loads(unb64(header_b64))
        payload = json.loads(unb64(payload_b64))
    except ValueError as e:
        raise Rejected(f"not a JWT: {e}")
    issuer, kid = header.get("iss"), header.get("kid", "")
    if header.get("alg") != "ES256" or not issuer or not kid.startswith(issuer + "#"):
        raise Rejected(f"unsupported JWT header (the mock expects ES256, iss, kid=<iss>#...): {header}")
    methods = [m for m in resolve(issuer)["verificationMethod"] if m["id"] == kid]
    if not methods:
        raise Rejected(f"{kid} is not in the DID document of {issuer}")
    jwk = methods[0]["publicKeyJwk"]
    public = ec.EllipticCurvePublicNumbers(int.from_bytes(unb64(jwk["x"]), "big"), int.from_bytes(unb64(jwk["y"]), "big"), ec.SECP256R1()).public_key()
    raw = unb64(signature_b64)
    try:
        public.verify(encode_dss_signature(int.from_bytes(raw[:32], "big"), int.from_bytes(raw[32:], "big")),
                      f"{header_b64}.{payload_b64}".encode(), ec.ECDSA(hashes.SHA256()))
    except Exception:
        raise Rejected(f"bad signature on a JWT issued by {issuer}")
    return issuer, payload


def notarize(vat_id, query):
    if not re.fullmatch(r"[A-Z]{2}[0-9A-Z]{2,13}", vat_id):
        raise Rejected(f"invalid VAT ID: {vat_id}")
    now = datetime.now(timezone.utc)
    return sign({
        "@context": [VC_CONTEXT, GX_CONTEXT],
        "type": ["VerifiableCredential", "gx:VatID"],
        "id": query.get("vcId", [f"http://{HOST}/notary/v2/credentials/{uuid.uuid4()}"])[0],
        "name": "VAT ID",
        "issuer": NOTARY_DID,
        "validFrom": now.isoformat(),
        "validUntil": (now + timedelta(days=90)).isoformat(),
        "credentialSubject": {"id": query.get("subjectId", [""])[0], "gx:vatID": vat_id, "gx:countryCode": vat_id[:2]},
        "evidence": {"gx:evidenceOf": "gx:VatID", "gx:evidenceURL": "MOCK: no registry was asked", "gx:executionDate": now.isoformat()},
    }, notary_key, NOTARY_DID, "vc")


def comply(presentation):
    participant, vp = verify(presentation)
    compliant, types = [], {}
    for entry in vp.get("verifiableCredential", []):
        envelope = entry.get("id", "")
        if not envelope.startswith("data:application/vc+jwt,"):
            raise Rejected("each credential must be enveloped: id = data:application/vc+jwt,<VC-JWT>")
        token = envelope.split(",", 1)[1]
        issuer, vc = verify(token)
        for t in vc.get("type", []):
            types.setdefault(t, []).append((issuer, vc))
        compliant.append({"id": vc.get("id"), "type": ",".join(t for t in vc.get("type", []) if t.startswith("gx:")),
                          "gx:digestSRI": "sha256-" + hashlib.sha256(token.encode()).hexdigest()})
    if not any(i == participant for i, _ in types.get("gx:LegalPerson", [])):
        raise Rejected("missing a gx:LegalPerson signed by the participant")
    if not any(i == participant and v.get("credentialSubject", {}).get("gaiaxTermsAndConditions") == TERMS_AND_CONDITIONS
               for i, v in types.get("gx:Issuer", [])):
        raise Rejected("missing a gx:Issuer signed by the participant, accepting the Gaia-X Terms and Conditions")
    if not any(i == NOTARY_DID for i, _ in types.get("gx:VatID", [])):
        raise Rejected(f"missing a registration number from a trusted notary ({NOTARY_DID})")
    now = datetime.now(timezone.utc)
    offer = f"http://{HOST}/credential-offers/{uuid.uuid4()}"
    return sign({
        "@context": [VC_CONTEXT, GX_CONTEXT],
        "type": ["VerifiableCredential", "gx:LabelCredential"],
        "id": offer,
        "issuer": COMPLIANCE_DID,
        "validFrom": now.isoformat(),
        "validUntil": (now + timedelta(days=90)).isoformat(),
        "credentialSubject": {"id": f"{offer}#cs", "gx:labelLevel": "SC", "gx:engineVersion": "mock",
                              "gx:rulesVersion": "CD25.03", "gx:compliantCredentials": compliant},
    }, compliance_key, COMPLIANCE_DID, "vc")


class Handler(BaseHTTPRequestHandler):
    def reply(self, code, body, content_type="application/json"):
        data = body.encode() if isinstance(body, str) else json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        url = urlparse(self.path)
        try:
            if url.path == "/health":
                self.reply(200, {"status": "up", "mock": True})
            elif url.path == "/x509CertificateChain.pem":
                self.reply(200, certificate_chain, "application/x-pem-file")
            elif url.path in documents:
                self.reply(200, documents[url.path], "application/did+json")
            elif url.path.startswith("/notary/v2/registration-numbers/vat-id/"):
                vat_id = unquote(url.path.rsplit("/", 1)[1])
                self.reply(200, notarize(vat_id, parse_qs(url.query)), "application/vc+jwt")
            else:
                self.reply(404, {"message": f"MOCK: nothing at {url.path}"})
        except Rejected as e:
            self.reply(400, {"message": f"MOCK: {e}"})

    def do_PUT(self):
        url = urlparse(self.path)
        if not url.path.endswith("/did.json") or url.path.startswith(("/notary/", "/compliance/")):
            self.reply(400, {"message": "MOCK: you can only publish a did.json, outside /notary and /compliance"})
            return
        documents[url.path] = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self.reply(204, "")

    def do_POST(self):
        url = urlparse(self.path)
        if url.path != "/compliance/v2/api/credential-offers/standard-compliance":
            self.reply(404, {"message": f"MOCK: nothing at {url.path}"})
            return
        try:
            body = self.rfile.read(int(self.headers["Content-Length"])).decode().strip()
            self.reply(201, comply(body), "application/vc+jwt")
        except Rejected as e:
            self.reply(400, {"message": f"MOCK: {e}"})


def demo_certificate(key):
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "gaiax-mock demo (not a trust anchor)")])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(now).not_valid_after(now + timedelta(days=90))
            .sign(key, hashes.SHA256()))
    return cert.public_bytes(serialization.Encoding.PEM).decode()


notary_key = ec.generate_private_key(ec.SECP256R1())
compliance_key = ec.generate_private_key(ec.SECP256R1())
documents = {
    "/notary/v2/did.json": did_document(NOTARY_DID, notary_key),
    "/compliance/v2/did.json": did_document(COMPLIANCE_DID, compliance_key),
}
certificate_chain = demo_certificate(compliance_key)

if __name__ == "__main__":
    print(f"MOCK GXDCH on http://{HOST}: notary {NOTARY_DID}, compliance {COMPLIANCE_DID}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
