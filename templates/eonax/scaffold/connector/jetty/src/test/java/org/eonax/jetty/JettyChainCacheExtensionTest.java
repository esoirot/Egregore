package org.eonax.jetty;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.jetty.JettyConfiguration;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.jetty.PortMappingRegistryImpl;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.jetty.ee10.servlet.FilterMapping;
import org.eclipse.jetty.ee10.servlet.ServletHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Given EDC's web server started, then a servlet added (as Jersey does), and one path whose cached
 * filter chain still leads to the 404 servlet of the empty context (what a request straddling the
 * servlet's registration leaves behind), when the extension starts, then that path reaches the servlet.
 */
class JettyChainCacheExtensionTest {

    private final HttpClient http = HttpClient.newHttpClient();
    private JettyService jetty;
    private int port;

    @BeforeEach
    void startJettyThenAddServlet() throws IOException {
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var ports = new PortMappingRegistryImpl();
        ports.register(new PortMapping("default", port, "/api"));
        jetty = new JettyService(new JettyConfiguration("password", "password"), new Monitor() {}, ports);
        jetty.start();
        jetty.registerServlet("default", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.getWriter().write("ok");
            }
        });
    }

    @AfterEach
    void stop() {
        jetty.shutdown();
    }

    @Test
    void aPathPoisonedAtStartReachesTheServletOnceTheExtensionStarts() throws Exception {
        poison("/api/check/readiness");
        assertEquals(404, get("/api/check/readiness"), "the poisoned path (the race's outcome)");

        var extension = new JettyChainCacheExtension();
        extension.webServer = jetty;
        extension.start();

        assertEquals(200, get("/api/check/readiness"));
        assertEquals(200, get("/api/check/liveness"));
    }

    /** Caches, for that path, a chain ending in a 404: what Jetty keeps when the race hits. */
    @SuppressWarnings("unchecked")
    private void poison(String path) throws Exception {
        ServletHandler handler = JettyChainCacheExtension.contexts(jetty).stream()
                .filter(c -> c.getServletHandler().getServlets().length > 1).findFirst().orElseThrow().getServletHandler();
        var cache = (ConcurrentMap<String, FilterChain>[]) field(ServletHandler.class, "_chainCache").get(handler);
        cache[FilterMapping.REQUEST].put(path, (request, response) -> ((HttpServletResponse) response).sendError(404));
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private int get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
