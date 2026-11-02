package queiroz.rodrigo.readforfree.concurrency;

import io.opentelemetry.context.Context;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class VirtualThreads {

    private VirtualThreads() {
    }

    public static ExecutorService newTracedExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("book-vt-", 0).factory();
        return Context.current().wrap(Executors.newThreadPerTaskExecutor(factory));
    }

    public static Throwable unwrap(Throwable error) {
        if ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            return unwrap(error.getCause());
        }
        return error;
    }
}
