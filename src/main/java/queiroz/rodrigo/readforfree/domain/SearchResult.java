package queiroz.rodrigo.readforfree.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SearchResult(
        String query,
        int totalResults,
        List<Book> books
) {
    public SearchResult {
        query = query == null ? "" : query.trim();
        books = books == null ? List.of() : List.copyOf(books);

        if (totalResults != books.size()) {
            throw new IllegalArgumentException("totalResults must match books.size()");
        }
    }

    @SafeVarargs
    public static SearchResult of(String query, List<Book>... repositoryResults) {
        return merge(query, repositoryResults == null ? List.of() : Arrays.asList(repositoryResults));
    }

    /**
     * Merges one result list per repository into a single result set.
     */
    public static SearchResult merge(String query, List<List<Book>> repositoryResults) {
        List<Book> merged = repositoryResults == null
                ? List.of()
                : repositoryResults.stream()
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .toList();
        return new SearchResult(query, merged.size(), merged);
    }
}
