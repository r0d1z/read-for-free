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
public class GutendexRepository {

    private static final String REPO = "gutendex";

    private final BookHttpClient http;
    private final String baseUrl;

    @Inject
    public GutendexRepository(
            BookHttpClient http,
            @ConfigProperty(name = "books.repos.gutendex.base-url", defaultValue = "https://gutendex.com") String baseUrl
    ) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public List<Book> search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonNode root = http.getJson(REPO, URI.create(baseUrl + "/books?search=" + encoded));

        List<Book> books = new ArrayList<>();
        for (JsonNode item : root.path("results")) {
            if (books.size() >= 5) break;

            String id = item.path("id").asText();
            if (id.isBlank()) continue;

            List<String> authors = new ArrayList<>();
            item.path("authors").forEach(a -> authors.add(a.path("name").asText()));

            JsonNode formats = item.path("formats");
            String format = formats.has("application/epub+zip") ? "epub" : "txt";
            String downloadUrl = formats.path("application/epub+zip")
                    .asText(formats.path("text/plain; charset=utf-8").asText(null));

            if (downloadUrl == null || downloadUrl.isBlank()) continue;

            books.add(Book.of(
                    id,
                    item.path("title").asText("Untitled"),
                    authors,
                    REPO,
                    format,
                    downloadUrl
            ));
        }
        return books;
    }
}
