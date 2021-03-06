package io.sesam.examples;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static spark.Spark.post;

public class App {

    static Logger log = LoggerFactory.getLogger(App.class);

    static final Set<String> SUPPORTED_SCHEMES = new HashSet<String>() {{
        add("http");
        add("https");
    }};

    public static URI normalizeURL(String url) throws URISyntaxException {
        // normalize
        URI uri;
        if (url.startsWith("~r")) {
            uri = new URI(url.substring(2));
        } else {
            uri = new URI(url);
        }
        if (!SUPPORTED_SCHEMES.contains(uri.getScheme())) {
            throw new URISyntaxException(url, "Unsupported URL schema");
        }
        return uri;
    }

    public static String extractContent(CloseableHttpClient client, Tika tika, String source)
            throws IOException {
        if (source != null) {
            if (source.startsWith("~b")) {
                // Decode embedded bytes
                String b64_string = source.substring(2);
                try {
                    byte[] decoded = Base64.getDecoder().decode(b64_string);
                    try (InputStream content = new ByteArrayInputStream(decoded)) {
                        return tika.parseToString(content);
                    } catch (TikaException e) {
                        log.error(String.format("Unable to extract content from '%s'", source), e);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn(String.format("Invalid Base64 encoded string: '%s'", b64_string));
                }
            } else {
                // Download content from URL
                try {
                    URI uri = normalizeURL(source);
                    HttpGet request = new HttpGet(uri);
                    request.addHeader("Connection", "Keep-Alive");
                    try (CloseableHttpResponse response = client.execute(request)) {
                        int statusCode = response.getStatusLine().getStatusCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            try (InputStream content = response.getEntity().getContent()) {
                                return tika.parseToString(content);
                            } catch (TikaException e) {
                                log.error(String.format("Unable to extract content from '%s'", source), e);
                            }
                        } else if (statusCode == 404) {
                            log.warn(String.format("URL not found: '%s'", source));
                        } else {
                            throw new IOException(String.format("URL '%s' returned status code: %d", source, statusCode));
                        }
                    }
                } catch (UnsupportedSchemeException | URISyntaxException | SSLHandshakeException e) {
                    log.warn(String.format("Invalid URL: '%s'", source));
                }
            }
        }
        return null;
    }

    private static String getStringEnv(String variable, String defaultValue) {
        String value = System.getenv(variable);
        if (value instanceof String) {
            return value;
        } else {
            return defaultValue;
        }
    }

    private static int getIntEnv(String variable, int defaultValue) {
        String value = System.getenv(variable);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBooleanEnv(String variable, boolean defaultValue) {
        String value = System.getenv(variable);
        if (value == null) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true");
    }

    public static CloseableHttpClient makeHttpClient() {
        ClientBuilder builder = new ClientBuilder();
        builder.setSocketTimeout(getIntEnv("SOCKET_TIMEOUT", 120) * 1000);  // 2 minutes
        builder.setConnectionTimeout(getIntEnv("CONNECTION_TIMEOUT", 10) * 1000);  // 10 seconds
        builder.setUsername(System.getenv("USERNAME"));
        builder.setPassword(System.getenv("PASSWORD"));
        builder.setDomain(System.getenv("DOMAIN"));
        builder.setWorkstation(System.getenv("WORKSTATION"));
        builder.setAuthType(getStringEnv("AUTH_TYPE", "basic"));
        builder.setTrustEverything(getBooleanEnv("TRUST_EVERYTHING", false));
        return builder.create();
    }

    public static void main(String[] args) {
        CloseableHttpClient client = makeHttpClient();
        Gson gson = new GsonBuilder().serializeNulls().create();
        Tika tika = new Tika();

        ExecutorService executor = Executors.newFixedThreadPool(getIntEnv("THREADS", 8));

        String sourceProperty = getStringEnv("SOURCE_PROPERTY", "url");
        String targetProperty = getStringEnv("TARGET_PROPERTY", "_content");

        post("/transform", (req, res) -> {
            // parse the request body into JSON elements
            JsonParser parser = new JsonParser();
            JsonElement root = null;
            try (Reader payload = new InputStreamReader(req.raw().getInputStream(), "utf-8")) {
                root = parser.parse(payload);
            }

            // get hold of the list of entities
            List<JsonObject> entities = new ArrayList<>();
            if (root.isJsonArray()) {
                JsonArray jsonArray = root.getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    if (element.isJsonObject()) {
                        entities.add(element.getAsJsonObject());
                    }
                }                
            } else if (root.isJsonObject()) {
                entities.add(root.getAsJsonObject());
            }

            // process entities in parallel
            AtomicBoolean failed = new AtomicBoolean(false);
            int size = entities.size();
            if (size > 0) {
                CountDownLatch doneSignal = new CountDownLatch(size);

                for (int i=0; i < size;  i++) {
                    // terminate loop if we're in failure mode
                    if (failed.get()) {
                        break;
                    }
                    JsonObject entity = entities.get(i);
                    Callable<JsonObject> task = () -> {
                        try {
                            JsonElement sourceElement = entity.get(sourceProperty);
                            if (sourceElement != null && sourceElement.isJsonPrimitive()) {
                                String source = sourceElement.getAsString();
                                String content = extractContent(client, tika, source);
                                entity.addProperty(targetProperty, content);
                            }
                        } catch (Exception e) {
                            String json = gson.toJson(entity);
                            log.error(String.format("Non-recoverable error while extracting content from %s", json), e);
                            failed.set(true);
                        } finally {
                            doneSignal.countDown();
                        }
                        return entity;
                    };
                    executor.submit(task);
                }
                // wait until all entities are done, or until we've entered failure mode
                while (!failed.get()) {
                    if (doneSignal.await(100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }
            }

            // output the response
            if (failed.get()) {
                res.type("text/plain");
                res.status(500);
                // res.body();
                Writer writer = res.raw().getWriter();
                writer.append("Non-recoverable error while extracting content.\n");
                writer.flush();
            } else {
                res.type("application/json");
                Writer writer = res.raw().getWriter();
                writer.append("[");
                for (int i=0; i < size;  i++) {
                    if (i > 0) {
                        writer.append(",");
                    }
                    gson.toJson(entities.get(i), writer);
                }
                writer.append("]");
                writer.flush();
            }
            return "";
        });
    }

}