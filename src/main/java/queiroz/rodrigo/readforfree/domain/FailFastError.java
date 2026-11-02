package queiroz.rodrigo.readforfree.domain;

import queiroz.rodrigo.readforfree.concurrency.VirtualThreads;

public record FailFastError(
        String error,
        String failedRepository,
        String message,
        boolean failFastCancelledSiblings,
        long timestamp
) {
    public FailFastError {
        error = defaultIfBlank(error, "Fail-Fast Circuit Tripped");
        failedRepository = defaultIfBlank(failedRepository, "unknown");
        message = defaultIfBlank(message, "unknown error");
    }

    public static FailFastError from(Throwable throwable) {
        Throwable root = VirtualThreads.unwrap(throwable);
        if (root == null) {
            return new FailFastError(
                    "Fail-Fast Circuit Tripped",
                    "unknown",
                    "unknown error",
                    true,
                    System.currentTimeMillis()
            );
        }

        String repository = (root instanceof RepositoryException re) ? re.repository() : "unknown";
        return new FailFastError(
                "Fail-Fast Circuit Tripped",
                repository,
                root.getMessage(),
                true,
                System.currentTimeMillis()
        );
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
