package queiroz.rodrigo.readforfree.domain;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainModelsTest {

    @Test
    void bookNormalizesNullAuthorsToEmptyList() {
        Book book = Book.of("1", "Refactoring", null, "repo", "pdf", "https://example.org/book.pdf");

        assertEquals(List.of(), book.authors());
    }

    @Test
    void bookCopiesAuthorsDefensively() {
        List<String> authors = new ArrayList<>(List.of("Martin Fowler"));
        Book book = Book.of("1", "Refactoring", authors, "repo", "pdf", "https://example.org/book.pdf");

        authors.add("Someone Else");

        assertEquals(List.of("Martin Fowler"), book.authors());
        assertThrows(UnsupportedOperationException.class, () -> book.authors().add("Hacker"));
    }

    @Test
    void bookDefaultsBlankTitle() {
        Book book = Book.of("1", " ", List.of(), "repo", "pdf", "https://example.org/book.pdf");

        assertEquals("Untitled", book.title());
    }

    @Test
    void bookRejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> Book.of("", "Title", List.of(), "repo", "pdf", "https://example.org/book.pdf"));
        assertThrows(IllegalArgumentException.class,
                () -> Book.of("1", "Title", List.of(), "", "pdf", "https://example.org/book.pdf"));
        assertThrows(IllegalArgumentException.class,
                () -> Book.of("1", "Title", List.of(), "repo", "pdf", " "));
    }

    @Test
    void bookRejectsNullAuthorElements() {
        List<String> authors = new ArrayList<>();
        authors.add("Author");
        authors.add(null);

        assertThrows(NullPointerException.class,
                () -> Book.of("1", "Title", authors, "repo", "pdf", "https://example.org/book.pdf"));
    }

    @Test
    void bookTrimsAndDropsBlankAuthors() {
        Book book = Book.of("1", "Title", List.of("  Mary Shelley  ", "", "   "), "repo", "pdf", "https://example.org/book.pdf");

        assertEquals(List.of("Mary Shelley"), book.authors());
    }

    @Test
    void bookLowercasesFormat() {
        Book book = Book.of("1", "Title", List.of(), "repo", "EPUB", "https://example.org/book.epub");

        assertEquals("epub", book.format());
    }

    @Test
    void searchResultMergesAndCountsBooks() {
        Book book = Book.of("1", "Title", List.of("Author"), "repo", "pdf", "https://example.org/book.pdf");

        SearchResult result = SearchResult.of("query", null, List.of(book));

        assertEquals("query", result.query());
        assertEquals(1, result.totalResults());
        assertEquals(List.of(book), result.books());
    }

    @Test
    void searchResultRejectsInconsistentTotal() {
        Book book = Book.of("1", "Title", List.of("Author"), "repo", "pdf", "https://example.org/book.pdf");

        assertThrows(IllegalArgumentException.class,
                () -> new SearchResult("query", 2, List.of(book)));
    }

    @Test
    void downloadRequestDefaultsOptionalFields() {
        DownloadRequest request = new DownloadRequest(null, " ", null, " https://example.org/book.pdf ");

        assertEquals("book", request.title());
        assertEquals("unknown", request.repository());
        assertEquals("txt", request.format());
        assertEquals("https://example.org/book.pdf", request.downloadUrl());
    }

    @Test
    void downloadRequestRejectsMissingDownloadUrlWhenDownloading() {
        DownloadRequest request = DownloadRequest.of("Title", "repo", "pdf", " ");

        assertThrows(IllegalArgumentException.class, request::requireDownloadable);
    }

    @Test
    void downloadRequestRejectsMalformedOrNonHttpUrls() {
        DownloadRequest withSpaces = DownloadRequest.of("Title", "repo", "pdf", "http://bad url/book.pdf");
        DownloadRequest fileScheme = DownloadRequest.of("Title", "repo", "pdf", "file:///etc/passwd");
        DownloadRequest missingHost = DownloadRequest.of("Title", "repo", "pdf", "https:///book.pdf");

        assertThrows(IllegalArgumentException.class, withSpaces::requireDownloadable);
        assertThrows(IllegalArgumentException.class, fileScheme::requireDownloadable);
        assertThrows(IllegalArgumentException.class, missingHost::requireDownloadable);
    }

    @Test
    void downloadRequestValidatesBatchEagerly() {
        assertThrows(IllegalArgumentException.class, () -> DownloadRequest.requireValidBatch(null));
        assertThrows(IllegalArgumentException.class, () -> DownloadRequest.requireValidBatch(List.of()));

        List<DownloadRequest> withNullItem = new ArrayList<>();
        withNullItem.add(null);
        assertThrows(IllegalArgumentException.class, () -> DownloadRequest.requireValidBatch(withNullItem));
    }

    @Test
    void downloadRequestRejectsUnsafeFormatWhenDownloading() {
        DownloadRequest request = DownloadRequest.of(
                "Title", "repo", "../evil", "https://example.org/book.pdf");

        assertThrows(IllegalArgumentException.class, request::requireDownloadable);
    }

    @Test
    void downloadRequestLowercasesFormat() {
        DownloadRequest request = DownloadRequest.of(
                "Title", "repo", "PDF", "https://example.org/book.pdf");

        assertEquals("pdf", request.format());
    }

    @Test
    void downloadReceiptRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadReceipt("Title", "repo", "book.pdf", -1, 0, "vt-1"));
        assertThrows(IllegalArgumentException.class,
                () -> new DownloadReceipt("Title", "repo", "book.pdf", 1, -1, "vt-1"));
    }

    @Test
    void failFastErrorNormalizesMissingFields() {
        FailFastError error = new FailFastError(null, null, null, true, 1);

        assertEquals("Fail-Fast Circuit Tripped", error.error());
        assertEquals("unknown", error.failedRepository());
        assertEquals("unknown error", error.message());
    }

    @Test
    void failFastErrorHandlesNullThrowable() {
        FailFastError error = FailFastError.from(null);

        assertEquals("unknown", error.failedRepository());
        assertEquals("unknown error", error.message());
    }

    @Test
    void repositoryExceptionNormalizesBlankRepository() {
        RepositoryException error = RepositoryException.upstreamStatus(" ", 503, URI.create("https://example.org"));

        assertEquals("unknown", error.repository());
    }

    @Test
    void repositoryExceptionHandlesMissingCauseMessage() {
        RepositoryException error = RepositoryException.ioFailure("repo", new RuntimeException());

        assertEquals("Repository [repo] failed: unknown error", error.getMessage());
    }
}
