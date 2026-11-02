package queiroz.rodrigo.readforfree.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import queiroz.rodrigo.readforfree.domain.SearchResult;
import queiroz.rodrigo.readforfree.service.BookSearchService;

/**
 * HTTP entry point for the load and chaos benchmark scenarios.
 */
@Path("/api/benchmark")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkResource {

    @Inject BookSearchService searchService;

    @GET
    @Path("/search")
    @RunOnVirtualThread
    public SearchResult benchmarkSearch(
            @QueryParam("q") @DefaultValue(BookSearchService.DEFAULT_QUERY) String query,
            @QueryParam("failRepo") String failRepo
    ) {
        return searchService.searchBenchmark(query, failRepo);
    }
}
