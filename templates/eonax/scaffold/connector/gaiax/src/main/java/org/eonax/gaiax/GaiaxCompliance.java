package org.eonax.gaiax;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gets a Gaia-X compliance credential for a participant and puts it in its wallet (Identity Hub).
 *
 * <p>The Gaia-X Loire flow (GXDCH compliance v2, notary v2), all credentials as VC-JWT (ES256):
 * <ol>
 *   <li>{@code did-document}: writes the participant's did:web document (key + X.509 chain URL), to
 *       publish at its domain before {@code comply}</li>
 *   <li>{@code comply}: checks that document is published, gets its registration number credential
 *       from the notary, signs a gx:LegalPerson and a gx:Issuer (acceptance of the Gaia-X Terms and
 *       Conditions), presents the three to the compliance service, and stores the gx:LabelCredential
 *       it returns in the participant's Identity Hub</li>
 * </ol>
 * Everything comes from the environment (GAIAX_*); this program does not know whether it talks to
 * the real GXDCH or to a stand-in. Intermediate credentials are written to GAIAX_OUTPUT.
 */
public final class GaiaxCompliance {

    // SHA-256 of the Gaia-X Terms and Conditions a participant accepts (gx:Issuer), as GXDCH expects it.
    static final String TERMS_AND_CONDITIONS = "4bd7554097444c960292b4726c2efa1373485e8a5565d94d41195214c5e0ceb3";
    static final String VC_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    static final String GX_CONTEXT = "https://w3id.org/gaia-x/development#";

    private final Map<String, String> env = System.getenv();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String did = require("GAIAX_DID");
    private final Path output = Path.of(require("GAIAX_OUTPUT"));
    private final ECKey key;

    private GaiaxCompliance() throws Exception {
        key = readKey(require("GAIAX_SIGNING_KEY"), did + "#key-0");
        Files.createDirectories(output);
    }

    /** A PKCS#8 EC P-256 private key (PEM, as openssl genpkey writes it); its public point is derived. */
    static ECKey readKey(String pem, String keyId) throws Exception {
        try (var parser = new PEMParser(new StringReader(pem))) {
            if (!(parser.readObject() instanceof PrivateKeyInfo info)
                    || !(new JcaPEMKeyConverter().getPrivateKey(info) instanceof ECPrivateKey privateKey)) {
                throw new IllegalArgumentException("GAIAX_SIGNING_KEY must be a PKCS#8 EC private key (PEM)");
            }
            var curve = ECNamedCurveTable.getParameterSpec("P-256");
            var point = curve.getG().multiply(privateKey.getS()).normalize();
            var publicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(
                    new ECPoint(point.getAffineXCoord().toBigInteger(), point.getAffineYCoord().toBigInteger()),
                    privateKey.getParams()));
            return new ECKey.Builder(Curve.P_256, publicKey).privateKey(privateKey).keyID(keyId).build();
        }
    }

    public static void main(String[] args) throws Exception {
        var command = args.length > 0 ? args[0] : "";
        var client = new GaiaxCompliance();
        switch (command) {
            case "did-document" -> client.writeDidDocument();
            case "comply" -> client.comply();
            default -> throw new IllegalArgumentException("Usage: did-document | comply");
        }
    }

    private void writeDidDocument() throws IOException {
        var jwk = new LinkedHashMap<>(key.toPublicJWK().toJSONObject());
        jwk.remove("kid");
        jwk.put("alg", "ES256");
        jwk.put("x5u", require("GAIAX_CERT_CHAIN_URL"));
        var method = map("id", did + "#key-0", "type", "JsonWebKey2020", "controller", did, "publicKeyJwk", jwk);
        var document = map(
                "@context", List.of("https://www.w3.org/ns/did/v1", "https://w3id.org/security/suites/jws-2020/v1"),
                "id", did,
                "verificationMethod", List.of(method),
                "assertionMethod", List.of(did + "#key-0"),
                "authentication", List.of(did + "#key-0"));
        Files.writeString(output.resolve("did.json"), JSONObjectUtils.toJSONString(document));
        System.out.println("did.json for " + did + ": publish it at " + didUrl(did));
    }

    private void comply() throws Exception {
        checkPublished();
        var base = didBaseUrl(did);
        var now = Instant.now();
        var until = now.plus(Duration.ofDays(90));

        // <type>:<number>, type as the notary's route names it (vat-id, lei-code, eori).
        var number = require("GAIAX_REGISTRATION_NUMBER").split(":", 2);
        var registrationId = base + "/registration-number.json";
        var registration = get(require("GAIAX_NOTARY_URL") + "/registration-numbers/" + number[0] + "/" + encode(number[1])
                + "?vcId=" + encode(registrationId) + "&subjectId=" + encode(registrationId + "#cs"), "the notary");
        save("registration-number.jwt", registration);
        System.out.println("✓ registration number " + number[0] + " " + number[1] + " notarized");

        var address = map("type", "gx:Address", "gx:countryCode", require("GAIAX_COUNTRY_CODE"));
        var legalPerson = sign(map(
                "@context", List.of(VC_CONTEXT, GX_CONTEXT, map("schema", "https://schema.org/")),
                "id", base + "/legal-person.json",
                "type", List.of("VerifiableCredential", "gx:LegalPerson"),
                "issuer", did, "validFrom", now.toString(), "validUntil", until.toString(),
                "credentialSubject", map(
                        "id", base + "/legal-person.json#cs",
                        "schema:name", require("GAIAX_LEGAL_NAME"),
                        "gx:registrationNumber", map("id", registrationId + "#cs"),
                        "gx:headquartersAddress", address,
                        "gx:legalAddress", address)), "vc");
        var termsAccepted = sign(map(
                "@context", List.of(VC_CONTEXT, GX_CONTEXT),
                "id", base + "/terms-and-conditions.json",
                "type", List.of("VerifiableCredential", "gx:Issuer"),
                "issuer", did, "validFrom", now.toString(), "validUntil", until.toString(),
                "credentialSubject", map("id", base + "/terms-and-conditions.json#cs", "gaiaxTermsAndConditions", TERMS_AND_CONDITIONS)), "vc");
        save("legal-person.jwt", legalPerson);
        save("terms-and-conditions.jwt", termsAccepted);

        var envelopes = new ArrayList<>();
        for (var vc : List.of(legalPerson, termsAccepted, registration)) {
            envelopes.add(map("@context", VC_CONTEXT, "id", "data:application/vc+jwt," + vc, "type", "EnvelopedVerifiableCredential"));
        }
        var presentation = sign(map(
                "@context", List.of(VC_CONTEXT), "type", "VerifiablePresentation", "verifiableCredential", envelopes,
                "issuer", did, "validFrom", now.toString(), "validUntil", until.toString()), "vp");
        save("presentation.jwt", presentation);

        var compliance = post(require("GAIAX_COMPLIANCE_URL") + "/api/credential-offers/standard-compliance?vcid="
                + encode(base + "/compliance.json"), "application/vp+jwt", presentation, "the compliance service");
        save("compliance.jwt", compliance);
        var label = payloadOf(compliance);
        @SuppressWarnings("unchecked")
        var subject = (Map<String, Object>) label.get("credentialSubject");
        System.out.println("✓ compliant: label " + subject.get("gx:labelLevel") + ", rules " + subject.get("gx:rulesVersion")
                + ", issued by " + label.get("issuer"));

        storeInWallet(compliance, label);
    }

    /** The compliance service resolves our DID: make sure the published document carries our key. */
    private void checkPublished() throws Exception {
        var url = didUrl(did);
        var published = JSONObjectUtils.parse(get(url, "your DID document (" + url + ")"));
        var expected = key.toPublicJWK();
        for (var method : JSONObjectUtils.getJSONObjectArray(published, "verificationMethod")) {
            var jwk = JSONObjectUtils.getJSONObject(method, "publicKeyJwk");
            if (jwk != null && expected.getX().toString().equals(jwk.get("x")) && expected.getY().toString().equals(jwk.get("y"))) {
                return;
            }
        }
        throw new IllegalStateException("The DID document at " + url + " does not carry the signing key: publish "
                + output.resolve("did.json") + " there (dataspace/gaiax writes it)");
    }

    private void storeInWallet(String jwt, Map<String, Object> vc) throws Exception {
        @SuppressWarnings("unchecked")
        var subject = new LinkedHashMap<>((Map<String, Object>) vc.get("credentialSubject"));
        var subjectId = subject.remove("id");
        var participant = require("GAIAX_WALLET_PARTICIPANT");
        var credential = map(
                "@context", vc.get("@context"), "id", vc.get("id"), "type", vc.get("type"),
                "issuer", map("id", vc.get("issuer"), "additionalProperties", map()),
                "issuanceDate", instant(vc.get("validFrom")), "expirationDate", instant(vc.get("validUntil")),
                "credentialSubject", List.of(map("id", subjectId, "claims", subject)),
                "dataModelVersion", "V_2_0");
        var manifest = map(
                "id", UUID.randomUUID().toString(), "participantContextId", participant,
                "verifiableCredentialContainer", map("rawVc", jwt, "format", "VC2_0_JOSE", "credential", credential));
        var request = HttpRequest.newBuilder(URI.create(require("GAIAX_WALLET_URL") + "/" + participant + "/credentials"))
                .header("x-api-key", require("GAIAX_WALLET_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSONObjectUtils.toJSONString(manifest)))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("The wallet refused the credential: HTTP " + response.statusCode() + " " + response.body());
        }
        System.out.println("✓ stored in the wallet of " + participant);
    }

    private String sign(Map<String, Object> payload, String typ) throws Exception {
        var header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(typ + "+jwt"))
                .contentType(typ)
                .keyID(did + "#key-0")
                .customParam("iss", did)
                .build();
        var jws = new JWSObject(header, new Payload(JSONObjectUtils.toJSONString(payload)));
        jws.sign(new ECDSASigner(key));
        return jws.serialize();
    }

    private String get(String url, String what) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Could not get " + what + ": HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body().strip();
    }

    private String post(String url, String type, String body, String what) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", type)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(what + " refused: HTTP " + response.statusCode() + " " + response.body());
        }
        return response.body().strip();
    }

    private void save(String name, String content) throws IOException {
        Files.writeString(output.resolve(name), content);
    }

    private static Map<String, Object> payloadOf(String jwt) throws Exception {
        return JWSObject.parse(jwt).getPayload().toJSONObject();
    }

    /** did:web:host%3Aport:a:b -> http(s)://host:port/a/b/did.json (no path: /.well-known/did.json). */
    private String didUrl(String did) {
        var base = didBaseUrl(did);
        return did.substring("did:web:".length()).contains(":") ? base + "/did.json" : base + "/.well-known/did.json";
    }

    private String didBaseUrl(String did) {
        if (!did.startsWith("did:web:")) {
            throw new IllegalArgumentException("GAIAX_DID must be a did:web: " + did);
        }
        var parts = did.substring("did:web:".length()).split(":");
        var url = new StringBuilder("true".equals(env.get("GAIAX_DID_WEB_USE_HTTP")) ? "http://" : "https://")
                .append(URLDecoder.decode(parts[0], StandardCharsets.UTF_8));
        for (var i = 1; i < parts.length; i++) {
            url.append('/').append(parts[i]);
        }
        return url.toString();
    }

    private String require(String name) {
        var value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String instant(Object timestamp) {
        return OffsetDateTime.parse(timestamp.toString()).toInstant().toString();
    }

    private static Map<String, Object> map(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
