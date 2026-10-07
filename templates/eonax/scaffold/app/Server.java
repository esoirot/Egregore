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
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * The travel app's server (just dev, run from the project root: java app/Server.java <bind-address>).
 * Serves the page, the travel app's own places (from its poi-backend), and the provider's timetable,
 * pulled through the data space at each request: the EDR of the transfer, then the provider's
 * data plane with its token. When the transfer is no longer running (stopped, contract ended), it
 * starts a new one (just transfer) once. The header X-Transfer-Process names the transfer used.
 */
public class Server {

    static final String MANAGEMENT = "http://consumer-controlplane:8081/management";
    static final String API_KEY = System.getenv().getOrDefault("CONSUMER_API_KEY", "consumer-api-key");
    static final Path TRANSFER_ID = Path.of(".dataspace/transfer-id.gtfs-demo-transit");
    static final HttpClient HTTP = HttpClient.newHttpClient();

    record Pull(String transfer, byte[] zip) {}

    public static void main(String[] args) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(args[0], 3000), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/")) {
                send(exchange, 200, "text/html", Files.readAllBytes(Path.of("app/index.html")));
            } else {
                send(exchange, 404, "text/plain", bytes("not found"));
            }
        });
        server.createContext("/data/pois.geojson", exchange -> respond(exchange, () -> {
            var pois = HTTP.send(HttpRequest.newBuilder(URI.create("http://poi-backend:8000/pois.geojson")).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            send(exchange, pois.statusCode(), "application/geo+json", pois.body());
        }));
        server.createContext("/data/", exchange -> respond(exchange, () -> {
            var pull = pull();
            var entry = exchange.getRequestURI().getPath().substring("/data/".length());
            exchange.getResponseHeaders().set("X-Transfer-Process", pull.transfer());
            var file = unzip(pull.zip(), entry);
            if (file == null) send(exchange, 404, "text/plain", bytes(entry + " is not in the provider's GTFS feed"));
            else send(exchange, 200, "text/plain", file);
        }));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
    }

    /** The provider's GTFS feed, pulled now under the running transfer (a new one if none is running). */
    static synchronized Pull pull() throws Exception {
        for (var attempt = 1; ; attempt++) {
            var transfer = Files.exists(TRANSFER_ID) ? Files.readString(TRANSFER_ID).trim() : "";
            // No EDR once the transfer stopped; a refused token once the contract ended.
            var edr = transfer.isEmpty() ? "{}" : management("/v3/edrs/" + transfer + "/dataaddress");
            if (field(edr, "endpoint") != null) {
                var data = HTTP.send(HttpRequest.newBuilder(URI.create(field(edr, "endpoint")))
                        .header("Authorization", field(edr, "authorization")).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (data.statusCode() == 200) return new Pull(transfer, data.body());
            }
            if (attempt == 2) throw new IOException("no timetable through the data space (transfer " + transfer + "): see just dev's output");
            System.out.println("Transfer " + transfer + " is not running: starting a new one (just transfer)");
            if (new ProcessBuilder("just", "transfer").inheritIO().start().waitFor() != 0) {
                throw new IOException("just transfer failed: see just dev's output");
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

    static void respond(HttpExchange exchange, Action action) throws IOException {
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
