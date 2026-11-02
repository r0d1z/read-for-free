package queiroz.rodrigo.readforfree.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import queiroz.rodrigo.readforfree.concurrency.FailFastFanOut;
import queiroz.rodrigo.readforfree.domain.Book;
import queiroz.rodrigo.readforfree.domain.SearchResult;
import queiroz.rodrigo.readforfree.repository.GutendexRepository;
import queiroz.rodrigo.readforfree.repository.InternetArchiveRepository;
import queiroz.rodrigo.readforfree.repository.MockBookRepository;
import queiroz.rodrigo.readforfree.repository.OpenLibraryRepository;

import java.util.List;
import java.util.function.Supplier;

/**
 * Application service that fans a query out to every catalog in parallel and
 * merges the results into a single {@link SearchResult}.
 */
@ApplicationScoped
public class BookSearchService {

    public static final String DEFAULT_QUERY = "Frankenstein";

    private static final String MOCK_GUTENBERG = "mock-gutenberg";
    private static final String MOCK_OPENLIBRARY = "mock-openlibrary";
    private static final String MOCK_ARCHIVE = "mock-archive";

    private final GutendexRepository gutendex;
    private final OpenLibraryRepository openLibrary;
    private final InternetArchiveRepository internetArchive;
    private final MockBookRepository mockRepositories;
    private final FailFastFanOut fanOut;

    @Inject
    public BookSearchService(
            GutendexRepository gutendex,
            OpenLibraryRepository openLibrary,
            InternetArchiveRepository internetArchive,
            MockBookRepository mockRepositories,
            FailFastFanOut fanOut
    ) {
        this.gutendex = gutendex;
        this.openLibrary = openLibrary;
        this.internetArchive = internetArchive;
        this.mockRepositories = mockRepositories;
        this.fanOut = fanOut;
    }

    /**
     * Searches the real public-domain catalogs.
     */
    public SearchResult search(String query) {
        String term = normalize(query);

        List<Supplier<List<Book>>> tasks = List.of(
                () -> gutendex.search(term),
                () -> openLibrary.search(term),
                () -> internetArchive.search(term)
        );

        return SearchResult.merge(term, fanOut.executeAll("search", tasks));
    }

    /**
     * Searches the WireMock-backed catalogs used by load and chaos tests.
     *
     * @param failRepository optional repository name that must answer with an
     *                       upstream failure, used to exercise fail-fast
     */
    public SearchResult searchBenchmark(String query, String failRepository) {
        String term = normalize(query);

        List<Supplier<List<Book>>> tasks = List.of(
                () -> mockRepositories.searchGutendex(term, MOCK_GUTENBERG.equalsIgnoreCase(failRepository)),
                () -> mockRepositories.searchOpenLibrary(term, MOCK_OPENLIBRARY.equalsIgnoreCase(failRepository)),
                () -> mockRepositories.searchArchive(term, MOCK_ARCHIVE.equalsIgnoreCase(failRepository))
        );

        return SearchResult.merge(term, fanOut.executeAll("search", tasks));
    }

    private static String normalize(String query) {
        return query == null || query.isBlank() ? DEFAULT_QUERY : query.trim();
    }
}
