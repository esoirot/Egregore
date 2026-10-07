package org.eonax.issuer;

import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationContext;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionValidatorRegistry;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSource;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactoryRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.ValidationResult;

import java.time.Instant;
import java.util.Map;

/**
 * The "membership" attestation: what the authority states about a member when it issues a
 * MembershipCredential (the credential definition maps these claims into credentialSubject).
 *
 * <p>An attestation is where an issuer checks its own records before signing. Here the record is
 * simply "registered as a holder": the Issuer Service only accepts credential requests from
 * holders the authority registered (just authority), so every request reaching this point comes
 * from a member. A real authority would look the member up in its registry (a database
 * attestation, an onboarding process). Adapted from EDC's Minimum Viable Dataspace (Apache-2.0).
 */
public class MembershipAttestationExtension implements ServiceExtension {

    @Inject
    private AttestationSourceFactoryRegistry sources;
    @Inject
    private AttestationDefinitionValidatorRegistry validators;

    @Override
    public void initialize(ServiceExtensionContext context) {
        sources.registerFactory("membership", definition -> membership());
        validators.registerValidator("membership", definition -> ValidationResult.success());
    }

    private static AttestationSource membership() {
        return (AttestationContext attestation) -> {
            var now = Instant.now().toString();
            return Result.success(Map.of(
                    "membership", Map.of("since", now),
                    "membershipType", "full-member",
                    "membershipStartDate", now,
                    "id", attestation.participantContextId()));
        };
    }
}
