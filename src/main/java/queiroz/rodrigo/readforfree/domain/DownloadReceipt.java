package queiroz.rodrigo.readforfree.domain;

import java.nio.file.Path;

public record DownloadReceipt(
        String title,
        String repository,
        String filePath,
        long bytesWritten,
        long elapsedMs,
        String virtualThreadName
) {
    public DownloadReceipt {
        title = defaultIfBlank(title, "book");
        repository = defaultIfBlank(repository, "unknown");

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (bytesWritten < 0) {
            throw new IllegalArgumentException("bytesWritten must not be negative");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("elapsedMs must not be negative");
        }

        virtualThreadName = defaultIfBlank(virtualThreadName, "unknown");
    }

    public static DownloadReceipt of(DownloadRequest request, Path filePath, long bytesWritten, long elapsedMs) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (filePath == null) {
            throw new IllegalArgumentException("filePath is required");
        }

        return new DownloadReceipt(
                request.title(),
                request.repository(),
                filePath.toString(),
                bytesWritten,
                elapsedMs,
                Thread.currentThread().getName()
        );
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
