package queiroz.rodrigo.readforfree.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import queiroz.rodrigo.readforfree.concurrency.VirtualThreads;
import queiroz.rodrigo.readforfree.domain.RepositoryException;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Callable;

@ApplicationScoped
public class BookTelemetry {

    private static final Logger LOG = Logger.getLogger(BookTelemetry.class);

    private static final AttributeKey<String> ATTR_OPERATION = AttributeKey.stringKey("operation");
    private static final AttributeKey<String> ATTR_REPOSITORY = AttributeKey.stringKey("repository");
    private static final AttributeKey<String> ATTR_STATUS = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ATTR_THREAD_NAME = AttributeKey.stringKey("thread.name");
    private static final AttributeKey<Boolean> ATTR_THREAD_IS_VIRTUAL = AttributeKey.booleanKey("thread.is_virtual");

    private final Tracer tracer;
    private final LongUpDownCounter activeVirtualThreads;
    private final LongCounter taskOutcomes;
    private final LongCounter failFastTriggers;
    private final DoubleHistogram repoDuration;
    private final LongCounter downloadBytes;

    @Inject
    public BookTelemetry(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("read-for-free");
        Meter meter = openTelemetry.getMeter("read-for-free");

        this.activeVirtualThreads = meter.upDownCounterBuilder("books.vt.active")
                .setDescription("Active Virtual Threads executing repository I/O")
                .setUnit("threads")
                .build();

        this.taskOutcomes = meter.counterBuilder("books.vt.tasks.total")
                .setDescription("Virtual Thread tasks completed by status")
                .setUnit("tasks")
                .build();

        this.failFastTriggers = meter.counterBuilder("books.vt.failfast.triggers")
                .setDescription("Fail-fast circuit trips cancelling sibling Virtual Threads")
                .setUnit("events")
                .build();

        this.repoDuration = meter.histogramBuilder("books.repository.duration")
                .setDescription("Duration of repository search or download operations")
                .setUnit("ms")
                .build();

        this.downloadBytes = meter.counterBuilder("books.download.bytes")
                .setDescription("Total book bytes streamed to disk")
                .setUnit("By")
                .build();
    }

    public void recordFailFast(String operation, Throwable error) {
        Throwable root = VirtualThreads.unwrap(error);
        String repository = (root instanceof RepositoryException re) ? re.repository() : "unknown";
        failFastTriggers.add(1, Attributes.of(
                ATTR_OPERATION, operation,
                ATTR_REPOSITORY, repository
        ));
        LOG.warnf("Fail-fast triggered on '%s' by '%s' — interrupting sibling Virtual Threads", operation, repository);
    }

    public void recordBytesDownloaded(String repository, long bytes) {
        if (bytes > 0) {
            downloadBytes.add(bytes, Attributes.of(ATTR_REPOSITORY, repository));
        }
    }

    public <T> T observe(String operation, String repository, Callable<T> action) {
        Attributes baseAttrs = Attributes.of(
                ATTR_OPERATION, operation,
                ATTR_REPOSITORY, repository
        );

        activeVirtualThreads.add(1, baseAttrs);
        long startNanos = System.nanoTime();
        Thread current = Thread.currentThread();

        Span span = tracer.spanBuilder(operation + ":" + repository)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(ATTR_OPERATION, operation)
                .setAttribute(ATTR_REPOSITORY, repository)
                .setAttribute(ATTR_THREAD_NAME, current.getName())
                .setAttribute(ATTR_THREAD_IS_VIRTUAL, current.isVirtual())
                .startSpan();

        try (Scope ignored = span.makeCurrent()) {
            T result = action.call();
            repoDuration.record(elapsedMs(startNanos), baseAttrs);
            taskOutcomes.add(1, Attributes.of(
                    ATTR_OPERATION, operation,
                    ATTR_REPOSITORY, repository,
                    ATTR_STATUS, "success"
            ));
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception ex) {
            repoDuration.record(elapsedMs(startNanos), baseAttrs);
            if (isInterrupted(ex) || current.isInterrupted()) {
                current.interrupt();
                taskOutcomes.add(1, Attributes.of(
                        ATTR_OPERATION, operation,
                        ATTR_REPOSITORY, repository,
                        ATTR_STATUS, "cancelled_by_fail_fast"
                ));
                span.addEvent("cancelled_by_sibling_failure");
                span.setStatus(StatusCode.ERROR, "Cancelled by fail-fast");
                throw RepositoryException.interrupted(repository, ex);
            }
            taskOutcomes.add(1, Attributes.of(
                    ATTR_OPERATION, operation,
                    ATTR_REPOSITORY, repository,
                    ATTR_STATUS, "failed"
            ));
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, ex.getMessage());
            if (ex instanceof RepositoryException re) {
                throw re;
            }
            throw RepositoryException.ioFailure(repository, ex);
        } finally {
            span.end();
            activeVirtualThreads.add(-1, baseAttrs);
        }
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static boolean isInterrupted(Throwable error) {
        for (Throwable curr = error; curr != null; curr = curr.getCause()) {
            if (curr instanceof InterruptedException
                    || curr instanceof InterruptedIOException
                    || curr instanceof ClosedByInterruptException) {
                return true;
            }
        }
        return false;
    }
}
