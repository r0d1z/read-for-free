package queiroz.rodrigo.readforfree.api;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import queiroz.rodrigo.readforfree.domain.FailFastError;
import queiroz.rodrigo.readforfree.domain.RepositoryException;

import java.util.Map;
import java.util.concurrent.CompletionException;

public class RestExceptionHandler {

    @ServerExceptionMapper
    public Response handleCompletionException(CompletionException ex) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(FailFastError.from(ex))
                .build();
    }

    @ServerExceptionMapper
    public Response handleRepositoryException(RepositoryException ex) {
        return Response.status(Response.Status.BAD_GATEWAY)
                .entity(FailFastError.from(ex))
                .build();
    }

    @ServerExceptionMapper
    public Response handleBadRequest(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "invalid request" : ex.getMessage();
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message))
                .build();
    }
}
