package queiroz.rodrigo.readforfree.api;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import queiroz.rodrigo.readforfree.domain.DownloadCatalog;
import queiroz.rodrigo.readforfree.domain.DownloadReceipt;
import queiroz.rodrigo.readforfree.domain.DownloadRequest;
import queiroz.rodrigo.readforfree.domain.SearchResult;
import queiroz.rodrigo.readforfree.service.BookDownloadService;
import queiroz.rodrigo.readforfree.service.BookSearchService;

import java.util.List;

/**
 * HTTP adapter only: parameter binding, status/JSON mapping and the Virtual
 * Threads execution mode. All orchestration lives in the services.
 */
@Path("/api/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {

    @Inject BookSearchService searchService;
    @Inject BookDownloadService downloadService;

    @GET
    @Path("/search")
    @RunOnVirtualThread
    public SearchResult search(@QueryParam("q") @DefaultValue(BookSearchService.DEFAULT_QUERY) String query) {
        return searchService.search(query);
    }

    @POST
    @Path("/download")
    @RunOnVirtualThread
    public DownloadReceipt download(DownloadRequest request) {
        return downloadService.download(request);
    }

    @POST
    @Path("/download/batch")
    @RunOnVirtualThread
    public List<DownloadReceipt> downloadBatch(List<DownloadRequest> requests) {
        return downloadService.downloadBatch(requests);
    }

    @GET
    @Path("/downloads")
    @RunOnVirtualThread
    public DownloadCatalog listDownloads() {
        return downloadService.listDownloads();
    }
}
