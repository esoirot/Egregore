package org.eonax.catalog;

import org.eclipse.edc.crawler.spi.TargetNode;
import org.eclipse.edc.crawler.spi.TargetNodeDirectory;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Tells the federated catalog crawler which participants to crawl: the data space's members, by DID.
 *
 * <p>Each member's DID document lists where its connector speaks the Dataspace Protocol (service of
 * type ProtocolEndpoint, set by just identity). The directory resolves the DIDs at every crawl, so a
 * member that joins later is found without a restart; a DID that does not resolve yet is skipped.
 * A real data space would read its member list from a registry (here: EONAX_CATALOG_PARTICIPANTS).
 */
public class MemberDirectoryExtension implements ServiceExtension {

    private static final String PROTOCOL = "dataspace-protocol-http:2025-1";

    @Setting(key = "eonax.catalog.participants", description = "DIDs of the members to crawl, comma-separated")
    private String participants;

    @Inject
    private DidResolverRegistry didResolver;

    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor().withPrefix("MemberDirectory");
    }

    @Provider
    public TargetNodeDirectory memberDirectory() {
        return new TargetNodeDirectory() {
            @Override
            public List<TargetNode> getAll() {
                return Arrays.stream(participants.split(","))
                        .map(String::trim)
                        .filter(did -> !did.isEmpty())
                        .map(this::resolve)
                        .filter(Objects::nonNull)
                        .toList();
            }

            private TargetNode resolve(String did) {
                var document = didResolver.resolve(did);
                if (document.failed()) {
                    monitor.debug("Not crawled (DID not resolvable yet): %s".formatted(did));
                    return null;
                }
                return document.getContent().getService().stream()
                        .filter(service -> "ProtocolEndpoint".equals(service.getType()))
                        .map(Service::getServiceEndpoint)
                        .findFirst()
                        .map(endpoint -> new TargetNode(did, did, endpoint, List.of(PROTOCOL)))
                        .orElse(null);
            }

            @Override
            public void insert(TargetNode node) {
                throw new UnsupportedOperationException("Members come from eonax.catalog.participants");
            }

            @Override
            public TargetNode remove(String id) {
                throw new UnsupportedOperationException("Members come from eonax.catalog.participants");
            }
        };
    }
}
