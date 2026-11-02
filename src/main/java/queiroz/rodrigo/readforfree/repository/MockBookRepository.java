package queiroz.rodrigo.readforfree.repository;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import queiroz.rodrigo.readforfree.domain.Book;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class MockBookRepository {

    private final BookHttpClient http;
    private final String baseUrl;

    @Inject
    public MockBookRepository(
            BookHttpClient http,
            @ConfigProperty(name = "books.repos.mock.base-url", defaultValue = "http://localhost:8089") String baseUrl
    ) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public List<Book> searchGutendex(String query, boolean fail) {
        return search("mock-gutenberg", "/mock/gutendex", query, fail);
    }

    public List<Book> searchOpenLibrary(String query, boolean fail) {
        return search("mock-openlibrary", "/mock/openlibrary", query, fail);
    }

    public List<Book> searchArchive(String query, boolean fail) {
        return search("mock-archive", "/mock/archive", query, fail);
    }

    private List<Book> search(String repoName, String pathPrefix, String query, boolean fail) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + pathPrefix + "/search?q=" + encoded + (fail ? "&fail=true" : ""));
        JsonNode root = http.getJson(repoName, uri);

        List<Book> books = new ArrayList<>();
        for (JsonNode item : root.path("books")) {
            String id = item.path("id").asText();
            if (id.isBlank()) continue;

            List<String> authors = new ArrayList<>();
            item.path("authors").forEach(a -> authors.add(a.asText()));

            String downloadUrl = item.path("downloadUrl").asText();
            if (downloadUrl.isBlank()) continue;
            if (downloadUrl.startsWith("/")) {
                downloadUrl = baseUrl + downloadUrl;
            }

            books.add(Book.of(
                    id,
                    item.path("title").asText("Untitled"),
                    authors,
                    repoName,
                    item.path("format").asText("txt"),
                    downloadUrl
            ));
        }
        return books;
    }
}
