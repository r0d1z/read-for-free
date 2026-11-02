package queiroz.rodrigo.readforfree.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import queiroz.rodrigo.readforfree.domain.RepositoryException;
import queiroz.rodrigo.readforfree.telemetry.BookTelemetry;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@ApplicationScoped
public class BookHttpClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenTelemetry openTelemetry;
    private final BookTelemetry telemetry;
    private final Duration timeout;
    private final int realPermits;
    private final int mockPermits;
    private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

    @Inject
    public BookHttpClient(
            ObjectMapper objectMapper,
            OpenTelemetry openTelemetry,
            BookTelemetry telemetry,
            @ConfigProperty(name = "books.http.timeout-seconds", defaultValue = "20") int timeoutSeconds,
            @ConfigProperty(name = "books.rate-limit.real-permits-per-repo", defaultValue = "4") int realPermits,
            @ConfigProperty(name = "books.rate-limit.mock-permits-per-repo", defaultValue = "5000") int mockPermits
    ) {
        this.objectMapper = objectMapper;
        this.openTelemetry = openTelemetry;
        this.telemetry = telemetry;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.realPermits = realPermits;
        this.mockPermits = mockPermits;
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(this.timeout)
                .build();
    }

    public JsonNode getJson(String repository, URI uri) {
        return telemetry.observe("search", repository, () -> {
            Semaphore limiter = limiterFor(repository);
            limiter.acquire();
            try {
                HttpRequest request = requestBuilder(uri)
                        .header("Accept", "application/json")
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw RepositoryException.upstreamStatus(repository, response.statusCode(), uri);
                }
                return objectMapper.readTree(response.body());
            } finally {
                limiter.release();
            }
        });
    }

    public long downloadToFile(String repository, URI uri, Path targetPath) {
        return telemetry.observe("download", repository, () -> {
            Semaphore limiter = limiterFor(repository);
            limiter.acquire();
            try {
                HttpRequest request = requestBuilder(uri).build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    throw RepositoryException.upstreamStatus(repository, response.statusCode(), uri);
                }

                Files.createDirectories(targetPath.getParent());
                Path partFile = targetPath.resolveSibling(targetPath.getFileName() + ".part");
                long totalBytes = 0;

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(partFile,
                             StandardOpenOption.CREATE,
                             StandardOpenOption.TRUNCATE_EXISTING,
                             StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[16_384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedException("Interrupted during book download");
                        }
                        out.write(buffer, 0, read);
                        totalBytes += read;
                    }
                } catch (Throwable t) {
                    Files.deleteIfExists(partFile);
                    throw t;
                }

                Files.move(partFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                telemetry.recordBytesDownloaded(repository, totalBytes);
                return totalBytes;
            } finally {
                limiter.release();
            }
        });
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", "ReadForFree/1.0 (Virtual Threads Demo)");
        openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), builder, HttpRequest.Builder::header);
        return builder;
    }

    private Semaphore limiterFor(String repository) {
        return semaphores.computeIfAbsent(repository, name ->
                new Semaphore(name.contains("mock") ? mockPermits : realPermits));
    }
}
