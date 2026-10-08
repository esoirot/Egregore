import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * The travel app's server (just dev, run from the project root: java app/Server.java <bind-address>).
 * Serves the page, the travel app's own places (from its poi-backend), and the provider's timetable,
 * pulled through the data space at each request: the EDR of the transfer, then the provider's
 * data plane with its token. When the transfer is no longer running (stopped, contract ended), it
 * starts a new one once (dataspace/transfer; first dataspace/catalog and negotiate when there is no
 * contract yet). The headers X-Transfer-Process and X-Pulled-At name the transfer and the pull time.
 * /api/status: a read-only summary of the data space (dataspace/status: offers, credentials, transfer).
 * Only GET. EONAX_APP_CACHE_SECONDS (default 0: every request pulls) keeps a pull and the status that
 * long: the deploy profile sets it, so anonymous visitors cannot make it pull at every request.
 */
public class Server {

    static final String MANAGEMENT = "http://consumer-controlplane:8081/management";
    static final String API_KEY = System.getenv().getOrDefault("CONSUMER_API_KEY", "consumer-api-key");
    static final Path TRANSFER_ID = Path.of(".dataspace/transfer-id.gtfs-demo-transit");
    static final Path AGREEMENT_ID = Path.of(".dataspace/agreement-id.gtfs-demo-transit");
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final Duration CACHE = Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("EONAX_APP_CACHE_SECONDS", "0")));

    record Pull(String transfer, byte[] zip, Instant at) {}
    record Status(byte[] json, Instant at) {}

    static Pull lastPull;
    static Status lastStatus;

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(args[0], 3000), 0);
        server.createContext("/", exchange -> respond(exchange, () -> {
            if (exchange.getRequestURI().getPath().equals("/")) {
                send(exchange, 200, "text/html", Files.readAllBytes(Path.of("app/index.html")));
            } else {
                send(exchange, 404, "text/plain", bytes("not found"));
            }
        }));
        server.createContext("/api/status", exchange -> respond(exchange, () -> send(exchange, 200, "application/json", status().json())));
        server.createContext("/data/pois.geojson", exchange -> respond(exchange, () -> {
            var pois = HTTP.send(HttpRequest.newBuilder(URI.create("http://poi-backend:8000/pois.geojson")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            send(exchange, pois.statusCode(), "application/geo+json", pois.body());
        }));
        server.createContext("/data/", exchange -> respond(exchange, () -> {
            var pull = pull();
            var entry = exchange.getRequestURI().getPath().substring("/data/".length());
            exchange.getResponseHeaders().set("X-Transfer-Process", pull.transfer());
            exchange.getResponseHeaders().set("X-Pulled-At", pull.at().toString());
            var file = unzip(pull.zip(), entry);
            if (file == null) send(exchange, 404, "text/plain", bytes(entry + " is not in the provider's GTFS feed"));
            else send(exchange, 200, "text/plain", file);
        }));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    /** The provider's GTFS feed, pulled under the running transfer (a new one if none is running). */
    static synchronized Pull pull() throws Exception {
        if (lastPull != null && lastPull.at().plus(CACHE).isAfter(Instant.now())) return lastPull;
        for (var attempt = 1; ; attempt++) {
            var transfer = Files.exists(TRANSFER_ID) ? Files.readString(TRANSFER_ID).trim() : "";
            // No EDR once the transfer stopped; a refused token once the contract ended.
            var edr = transfer.isEmpty() ? "{}" : management("/v3/edrs/" + transfer + "/dataaddress");
            if (field(edr, "endpoint") != null) {
                var data = HTTP.send(HttpRequest.newBuilder(URI.create(field(edr, "endpoint")))
                        .header("Authorization", field(edr, "authorization")).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (data.statusCode() == 200) return lastPull = new Pull(transfer, data.body(), Instant.now());
            }
            if (attempt == 2) throw new IOException("no timetable through the data space (transfer " + transfer + "): see the server's output");
            System.out.println("Transfer " + transfer + " is not running: starting a new one");
            if (!Files.exists(AGREEMENT_ID)) run("dataspace/catalog", "dataspace/negotiate");
            run("dataspace/transfer");
        }
    }

    /** The data space summary (dataspace/status), at most CACHE old. */
    static synchronized Status status() throws Exception {
        if (lastStatus == null || !lastStatus.at().plus(CACHE).isAfter(Instant.now())) {
            var process = new ProcessBuilder("dataspace/status").redirectError(ProcessBuilder.Redirect.INHERIT).start();
            var json = process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) throw new IOException("dataspace/status failed: see the server's output");
            lastStatus = new Status(json, Instant.now());
        }
        return lastStatus;
    }

    static void run(String... scripts) throws Exception {
        for (var script : scripts) {
            if (new ProcessBuilder(script).inheritIO().start().waitFor() != 0) {
                throw new IOException(script + " failed: see the server's output");
            }
        }
    }

    static String management(String path) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(URI.create(MANAGEMENT + path)).header("X-Api-Key", API_KEY).build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : "{}";
    }

    /** A top-level string field of a small JSON answer (the JDK has no JSON parser). */
    static String field(String json, String name) {
        var match = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return match.find() ? match.group(1) : null;
    }

    static byte[] unzip(byte[] zip, String entry) throws IOException {
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                if (e.getName().equals(entry)) return in.readAllBytes();
            }
            return null;
        }
    }

    interface Action { void run() throws Exception; }

    /** Runs a GET (anything else: 405, the app is read only); a failure answers 502 with its message. */
    static void respond(HttpExchange exchange, Action action) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, "text/plain", bytes("read only"));
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            send(exchange, 502, "text/plain", bytes(String.valueOf(e.getMessage())));
        }
    }

    static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
    }

    static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
