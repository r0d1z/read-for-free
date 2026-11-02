package queiroz.rodrigo.readforfree.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record Book(
        String id,
        String title,
        List<String> authors,
        String repository,
        String format,
        String downloadUrl
) {
    public Book {
        id = requireText(id, "id");
        title = defaultIfBlank(title, "Untitled");
        authors = normalizeAuthors(authors);
        repository = requireText(repository, "repository");
        format = requireText(format, "format").toLowerCase(Locale.ROOT);
        downloadUrl = requireText(downloadUrl, "downloadUrl");
    }

    public static Book of(
            String id,
            String title,
            List<String> authors,
            String repository,
            String format,
            String downloadUrl
    ) {
        return new Book(id, title, authors, repository, format, downloadUrl);
    }

    private static List<String> normalizeAuthors(List<String> authors) {
        if (authors == null) {
            return List.of();
        }
        return authors.stream()
                .map(author -> Objects.requireNonNull(author, "author must not be null").trim())
                .filter(author -> !author.isEmpty())
                .toList();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
