package queiroz.rodrigo.readforfree.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

public record DownloadRequest(
        String title,
        String repository,
        String format,
        String downloadUrl
) {
    public DownloadRequest {
        title = defaultIfBlank(title, "book");
        repository = defaultIfBlank(repository, "unknown");
        format = defaultIfBlank(format, "txt").toLowerCase(Locale.ROOT);
        downloadUrl = downloadUrl == null ? null : downloadUrl.trim();
    }

    /**
     * Validates the fields required to execute a download.
     *
     * <p>Kept out of the compact constructor on purpose: this record is also
     * deserialized from HTTP input, and throwing here would make Jackson wrap
     * the error before the REST layer can expose its message.</p>
     */
    public DownloadRequest requireDownloadable() {
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("downloadUrl is required");
        }
        validateHttpUri(downloadUrl);
        if (!format.matches("[a-z0-9]+")) {
            throw new IllegalArgumentException("format must contain only letters and digits");
        }
        return this;
    }

    public static List<DownloadRequest> requireValidBatch(List<DownloadRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("books list cannot be empty");
        }
        requests.forEach(request -> {
            if (request == null) {
                throw new IllegalArgumentException("request is required");
            }
            request.requireDownloadable();
        });
        return requests;
    }

    public static DownloadRequest of(String title, String repository, String format, String downloadUrl) {
        return new DownloadRequest(title, repository, format, downloadUrl);
    }

    private static void validateHttpUri(String rawUrl) {
        final URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("downloadUrl must be a valid HTTP(S) URI", e);
        }
        String scheme = uri.getScheme();
        boolean validHttpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!validHttpScheme || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("downloadUrl must be a valid HTTP(S) URI");
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
