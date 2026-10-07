package org.eonax.dataplane;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static org.eclipse.edc.spi.constants.CoreConstants.EDC_NAMESPACE;

/**
 * Serves an HTTP pull: no token, 401; a token the data plane did not issue (or expired), 403;
 * otherwise the data, fetched from the asset's real location (its dataAddress baseUrl), which the
 * consumer never learns.
 */
@Path("{any:.*}")
@Produces(WILDCARD)
public class PublicApiController {

    private final DataPlaneAuthorizationService authorizationService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    PublicApiController(DataPlaneAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @GET
    public Response get(@Context ContainerRequestContext request) {
        var token = request.getHeaderString(AUTHORIZATION);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        var authorization = authorizationService.authorize(token, Map.of());
        if (authorization.failed()) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        var source = authorization.getContent();
        var path = request.getUriInfo().getPath().replaceFirst("^/+", "");
        var target = source.getStringProperty(EDC_NAMESPACE + "baseUrl") + (path.isEmpty() ? "" : "/" + path);
        try {
            var response = httpClient.send(HttpRequest.newBuilder(URI.create(target)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            return Response.status(response.statusCode())
                    .header(CONTENT_TYPE, response.headers().firstValue(CONTENT_TYPE).orElse(APPLICATION_OCTET_STREAM))
                    .entity(response.body())
                    .build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_GATEWAY).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(Response.Status.BAD_GATEWAY).build();
        }
    }
}
