package queiroz.rodrigo.readforfree.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import queiroz.rodrigo.readforfree.domain.DownloadReceipt;
import queiroz.rodrigo.readforfree.domain.DownloadRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@ApplicationScoped
public class BookDownloader {

    private final BookHttpClient http;
    private final Path storageDir;

    @Inject
    public BookDownloader(
            BookHttpClient http,
            @ConfigProperty(name = "books.storage.directory", defaultValue = "downloads") String storageDirectory
    ) {
        this.http = http;
        this.storageDir = Paths.get(storageDirectory).toAbsolutePath().normalize();
    }

    public DownloadReceipt download(DownloadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        request.requireDownloadable();

        long startNanos = System.nanoTime();
        String filename = slugify(request.title()) + "_" + slugify(request.repository()) + "." + request.format();
        Path targetPath = storageDir.resolve(filename);

        long bytes = http.downloadToFile(request.repository(), URI.create(request.downloadUrl()), targetPath);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        return DownloadReceipt.of(request, targetPath, bytes, elapsedMs);
    }

    public Path storageDirectory() {
        return storageDir;
    }

    public List<String> listFiles() {
        if (!Files.exists(storageDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(storageDir)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.endsWith(".part"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "book";
        }
        return value.replaceAll("[^a-zA-Z0-9.-]", "_")
                .replaceAll("_+", "_")
                .toLowerCase(Locale.ROOT);
    }
}
