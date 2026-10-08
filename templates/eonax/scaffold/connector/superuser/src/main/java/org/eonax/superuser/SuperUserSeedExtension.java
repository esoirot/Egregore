package org.eonax.superuser;

import org.eclipse.edc.identityhub.spi.participantcontext.IdentityApiScopes;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.IssuerAdminApiScopes;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.List;
import java.util.Map;

/**
 * Creates the super-user account of an Identity Hub or Issuer Service at start, so that operators
 * (here: the dataspace/ scripts) can create participant contexts through the Identity API with a
 * known API key (header x-api-key). Every other account gets its own key when it is created.
 *
 * <p>An API key reads {@code base64(account id) + "." + base64(secret)}; the default in
 * compose.template.yaml is {@code base64("super-user") + "." + base64("super-secret-key")}.
 * Set your own with IDENTITYHUB_SUPERUSER_KEY / ISSUER_SUPERUSER_KEY in .env. EDC IdentityHub
 * 0.18 has no such seeding of its own (its tests create the account in code).
 */
public class SuperUserSeedExtension implements ServiceExtension {

    private static final String SUPER_USER = "super-user";

    @Setting(key = "edc.ih.api.superuser.key",
            description = "API key of the super-user account: base64(\"super-user\") + \".\" + base64(secret)")
    private String superUserKey;

    @Inject
    private IdentityHubParticipantContextService participants;
    @Inject
    private Vault vault;

    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor().withPrefix("SuperUser");
    }

    @Override
    public void start() {
        if (participants.getParticipantContext(SUPER_USER).failed()) {
            var manifest = ParticipantManifest.Builder.newInstance()
                    .participantContextId(SUPER_USER)
                    .did("did:web:" + SUPER_USER)
                    .active(true)
                    .scopes(List.of(IdentityApiScopes.ADMIN, IssuerAdminApiScopes.ADMIN))
                    .key(KeyDescriptor.Builder.newInstance()
                            .keyId(SUPER_USER + "-key")
                            .privateKeyAlias(SUPER_USER + "-key")
                            .keyGeneratorParams(Map.of("algorithm", "EC"))
                            .build())
                    .build();
            participants.createParticipantContext(manifest)
                    .orElseThrow(f -> new EdcException("Cannot create the super-user: " + f.getFailureDetail()));
            monitor.info("Created the super-user account");
        }
        // Set (or reset) its API key to the configured one.
        var alias = participants.getParticipantContext(SUPER_USER)
                .orElseThrow(f -> new EdcException(f.getFailureDetail()))
                .getApiTokenAlias();
        vault.storeSecret(SUPER_USER, alias, superUserKey)
                .orElseThrow(f -> new EdcException("Cannot store the super-user API key: " + f.getFailureDetail()));
    }
}
