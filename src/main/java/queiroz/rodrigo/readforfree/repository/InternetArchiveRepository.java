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
public class InternetArchiveRepository {

    private static final String REPO = "internet-archive";

    private final BookHttpClient http;
    private final String baseUrl;

    @Inject
    public InternetArchiveRepository(
            BookHttpClient http,
            @ConfigProperty(name = "books.repos.internet-archive.base-url", defaultValue = "https://archive.org") String baseUrl
    ) {
        this.http = http;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public List<Book> search(String query) {
        String lucene = URLEncoder.encode("title:(" + query + ") AND mediatype:texts", StandardCharsets.UTF_8);
        URI uri = URI.create(baseUrl + "/advancedsearch.php?q=" + lucene
                + "&fl[]=identifier&fl[]=title&fl[]=creator&output=json&rows=4");
        JsonNode root = http.getJson(REPO, uri);

        List<Book> books = new ArrayList<>();
        for (JsonNode doc : root.path("response").path("docs")) {
            String id = doc.path("identifier").asText();
            if (id.isBlank()) continue;

            List<String> authors = new ArrayList<>();
            JsonNode creator = doc.path("creator");
            if (creator.isArray()) {
                creator.forEach(c -> authors.add(c.asText()));
            } else if (creator.isTextual()) {
                authors.add(creator.asText());
            }

            books.add(Book.of(
                    id,
                    doc.path("title").asText("Untitled"),
                    authors,
                    REPO,
                    "epub",
                    baseUrl + "/download/" + id + "/" + id + ".epub"
            ));
        }
        return books;
    }
}
