package org.eonax.jetty;

import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.web.jetty.JettyService;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;

import java.util.Collection;
import java.util.Map;

/**
 * Turns off Jetty's filter chain cache in every web context of this runtime.
 *
 * <p>EDC 0.18.1 starts Jetty (ports open) before Jersey adds its servlets, and Jetty 12.1 caches each
 * request's filter chain by path alone. A request that picked the empty context's 404 servlet just
 * before Jersey's servlet arrived can store its chain after the cache was flushed: from then on, that
 * one path answers 404 (405 for a POST) until the runtime restarts, while every other path works. Seen
 * after a full dev up on the health probe (/api/check/readiness) and on the data plane's registration
 * (/control/v1/dataplanes), which then made the data plane fail to start. Without the cache, every
 * request looks up its servlet; the cost is a few objects per request.
 */
public class JettyChainCacheExtension implements ServiceExtension {

    @Inject // EDC's JettyService; injecting it also starts this extension after Jetty
    WebServer webServer;

    @Override
    public String name() {
        return "Jetty chain cache off";
    }

    @Override
    public void start() {
        contexts(webServer).forEach(context -> context.getServletHandler().setFilterChainsCached(false));
    }

    @SuppressWarnings("unchecked")
    static Collection<ServletContextHandler> contexts(WebServer server) {
        try {
            var field = JettyService.class.getDeclaredField("handlers");
            field.setAccessible(true);
            return ((Map<String, ServletContextHandler>) field.get(server)).values();
        } catch (ReflectiveOperationException e) {
            throw new EdcException("Cannot reach Jetty's contexts: EDC's JettyService changed", e);
        }
    }
}
