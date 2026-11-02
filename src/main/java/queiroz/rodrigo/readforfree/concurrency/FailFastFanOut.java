package queiroz.rodrigo.readforfree.concurrency;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import queiroz.rodrigo.readforfree.telemetry.BookTelemetry;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Runs a group of independent tasks concurrently on Virtual Threads and aborts
 * the whole group as soon as the first one fails.
 *
 * <p>{@link CompletableFuture#allOf} alone neither fails fast nor interrupts the
 * tasks still running, so this coordinator trips the shared future and calls
 * {@link ExecutorService#shutdownNow()} to interrupt the sibling Virtual Threads
 * (aborting in-flight sockets and cleaning up partial downloads).</p>
 *
 * <p>Results are returned in submission order, which keeps callers independent
 * from completion order.</p>
 */
@ApplicationScoped
public class FailFastFanOut {

    private final BookTelemetry telemetry;

    @Inject
    public FailFastFanOut(BookTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    public <T> List<T> executeAll(String operation, List<? extends Supplier<T>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        try (ExecutorService executor = VirtualThreads.newTracedExecutor()) {

            List<CompletableFuture<T>> futures = tasks.stream()
                    .map(task -> CompletableFuture.supplyAsync(task, executor))
                    .toList();

            // Short-circuit controller: the first failure completes this future
            // exceptionally and unblocks the waiting Virtual Thread.
            CompletableFuture<Void> all = CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new));

            futures.forEach(future -> future.exceptionally(failure -> {
                if (all.completeExceptionally(failure)) {
                    telemetry.recordFailFast(operation, failure);
                    executor.shutdownNow(); // interrupts sibling Virtual Threads
                }
                return null;
            }));

            all.join();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
        }
    }
}
