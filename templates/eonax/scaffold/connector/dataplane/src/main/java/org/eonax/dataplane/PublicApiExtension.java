package org.eonax.dataplane;

import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.runtime.metamodel.annotation.Settings;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

/**
 * The data plane's public API, where a consumer pulls data (HTTP pull).
 *
 * <p>When a transfer starts, the data plane hands the consumer an EDR: this endpoint plus a
 * short-lived token. {@link PublicApiController} checks the token and streams the data from the
 * provider's backend. Since EDC 0.18 every data plane brings its own public API; this one follows
 * EDC's transfer-03 sample (Apache-2.0).
 */
public class PublicApiExtension implements ServiceExtension {

    @Configuration
    private PublicApiConfiguration configuration;

    @Setting(key = "edc.dataplane.proxy.public.endpoint",
            description = "URL of this public API as consumers reach it, without trailing slash")
    private String publicEndpoint;

    @Inject
    private PortMappingRegistry portMappingRegistry;
    @Inject
    private PublicEndpointGeneratorService endpointGenerator;
    @Inject
    private WebService webService;
    @Inject
    private DataPlaneAuthorizationService authorizationService;

    @Override
    public void initialize(ServiceExtensionContext context) {
        portMappingRegistry.register(new PortMapping("public", configuration.port(), configuration.path()));
        // The endpoint that goes into the EDR for HttpData transfers.
        endpointGenerator.addGeneratorFunction("HttpData", dataAddress -> Endpoint.url(publicEndpoint));
        webService.registerResource("public", new PublicApiController(authorizationService));
    }

    @Settings
    record PublicApiConfiguration(
            @Setting(key = "web.http.public.port", description = "Port of the public API", defaultValue = "8085")
            int port,
            @Setting(key = "web.http.public.path", description = "Path of the public API", defaultValue = "/public")
            String path
    ) {
    }
}
