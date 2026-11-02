package queiroz.rodrigo.readforfree.concurrency;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import queiroz.rodrigo.readforfree.domain.Book;
import queiroz.rodrigo.readforfree.domain.RepositoryException;
import queiroz.rodrigo.readforfree.telemetry.BookTelemetry;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class FailFastFanOutTest {

    private final FailFastFanOut fanOut = new FailFastFanOut(new BookTelemetry(OpenTelemetry.noop()));

    @Test
    @DisplayName("Should run every task in parallel on Virtual Threads and keep submission order")
    void shouldRunInParallelOnVirtualThreads() {
        long start = System.currentTimeMillis();

        List<List<Book>> results = fanOut.executeAll("search", List.of(
                () -> simulateSearch("gutendex", 100),
                () -> simulateSearch("openlibrary", 100),
                () -> simulateSearch("internet-archive", 100)
        ));

        long elapsed = System.currentTimeMillis() - start;
        assertEquals(3, results.size());
        assertEquals("gutendex", results.get(0).get(0).repository());
        assertEquals("openlibrary", results.get(1).get(0).repository());
        assertEquals("internet-archive", results.get(2).get(0).repository());
        assertTrue(elapsed < 260, "Parallel execution should finish in < 260ms, took " + elapsed + "ms");
    }

    @Test
    @DisplayName("The first failure should abort the wait and interrupt sibling Virtual Threads")
    void shouldFailFastAndInterruptSiblingVirtualThreads() throws InterruptedException {
        CountDownLatch siblingsInterrupted = new CountDownLatch(2);
        long start = System.currentTimeMillis();

        CompletionException thrown = assertThrows(CompletionException.class, () -> fanOut.executeAll("search", List.of(
                () -> simulateSlowInterruptible("gutendex", 4000, siblingsInterrupted),
                () -> {
                    sleepQuietly(60);
                    throw RepositoryException.upstreamStatus("internet-archive", 503, URI.create("https://archive.org"));
                },
                () -> simulateSlowInterruptible("openlibrary", 4000, siblingsInterrupted)
        )));

        long elapsed = System.currentTimeMillis() - start;
        Throwable root = VirtualThreads.unwrap(thrown);

        assertInstanceOf(RepositoryException.class, root);
        assertEquals("internet-archive", ((RepositoryException) root).repository());
        assertTrue(elapsed < 800, "Should fail fast in < 800ms instead of 4000ms, took " + elapsed + "ms");
        assertTrue(siblingsInterrupted.await(500, TimeUnit.MILLISECONDS),
                "Both slow sibling Virtual Threads must receive InterruptedException");
    }

    @Test
    @DisplayName("Should return an empty list when there is nothing to fan out")
    void shouldHandleEmptyTaskList() {
        assertTrue(fanOut.executeAll("search", List.<Supplier<List<Book>>>of()).isEmpty());
    }

    private static List<Book> simulateSearch(String repo, long delayMs) {
        assertTrue(Thread.currentThread().isVirtual(), "Must execute on a Virtual Thread");
        sleepQuietly(delayMs);
        return List.of(Book.of("1", "Frankenstein", List.of("Mary Shelley"), repo, "epub", "https://example.org/1.epub"));
    }

    private static List<Book> simulateSlowInterruptible(String repo, long delayMs, CountDownLatch latch) {
        try {
            Thread.sleep(delayMs);
            return List.of();
        } catch (InterruptedException e) {
            latch.countDown();
            Thread.currentThread().interrupt();
            throw RepositoryException.interrupted(repo, e);
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
