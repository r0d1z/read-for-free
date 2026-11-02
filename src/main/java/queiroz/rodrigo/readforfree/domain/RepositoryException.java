package queiroz.rodrigo.readforfree.domain;

import java.net.URI;

public class RepositoryException extends RuntimeException {

    private final String repository;

    private RepositoryException(String repository, String message, Throwable cause) {
        super(message, cause);
        this.repository = repositoryName(repository);
    }

    public static RepositoryException upstreamStatus(String repository, int statusCode, URI uri) {
        String repo = repositoryName(repository);
        return new RepositoryException(
                repo,
                "Repository [" + repo + "] returned HTTP " + statusCode + " for " + uri,
                null
        );
    }

    public static RepositoryException ioFailure(String repository, Throwable cause) {
        String repo = repositoryName(repository);
        String detail = cause == null || cause.getMessage() == null
                ? "unknown error"
                : cause.getMessage();
        return new RepositoryException(
                repo,
                "Repository [" + repo + "] failed: " + detail,
                cause
        );
    }

    public static RepositoryException interrupted(String repository, Throwable cause) {
        String repo = repositoryName(repository);
        return new RepositoryException(
                repo,
                "Virtual thread interrupted on [" + repo + "]",
                cause
        );
    }

    public String repository() {
        return repository;
    }

    private static String repositoryName(String repository) {
        return repository == null || repository.isBlank() ? "unknown" : repository.trim();
    }
}
