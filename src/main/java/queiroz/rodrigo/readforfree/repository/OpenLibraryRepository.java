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
public class OpenLibraryRepository {

    private static final String REPO = "openlibrary";

    private final BookHttpClient http;
    private final String baseUrl;

    @Inject
    public OpenLibraryRepository(
            BookHttpClient http,
            @ConfigProperty(name = "books.repos.openlibrary.base-url", defaultValue = "https://openlibrary.org") String baseUrl
    ) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public List<Book> search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/search.json?q=" + encoded + "&has_fulltext=true&public_scan_b=true&limit=5");
        JsonNode root = http.getJson(REPO, uri);

        List<Book> books = new ArrayList<>();
        for (JsonNode doc : root.path("docs")) {
            JsonNode iaList = doc.path("ia");
            if (!iaList.isArray() || iaList.isEmpty()) continue;

            String iaId = iaList.get(0).asText();
            if (iaId.isBlank()) continue;

            String key = doc.path("key").asText();
            if (key.isBlank()) continue;

            List<String> authors = new ArrayList<>();
            doc.path("author_name").forEach(a -> authors.add(a.asText()));

            books.add(Book.of(
                    key,
                    doc.path("title").asText("Untitled"),
                    authors,
                    REPO,
                    "pdf",
                    "https://archive.org/download/" + iaId + "/" + iaId + ".pdf"
            ));
        }
        return books;
    }
}
