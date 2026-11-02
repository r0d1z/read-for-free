package queiroz.rodrigo.readforfree.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import queiroz.rodrigo.readforfree.concurrency.FailFastFanOut;
import queiroz.rodrigo.readforfree.domain.DownloadCatalog;
import queiroz.rodrigo.readforfree.domain.DownloadReceipt;
import queiroz.rodrigo.readforfree.domain.DownloadRequest;
import queiroz.rodrigo.readforfree.repository.BookDownloader;

import java.util.List;
import java.util.function.Supplier;

/**
 * Application service for single and batch book downloads.
 *
 * <p>Validates the request before starting any I/O and runs batches through
 * {@link FailFastFanOut}, so one failing book cancels the sibling downloads
 * instead of leaving partial files behind.</p>
 */
@ApplicationScoped
public class BookDownloadService {

    private final BookDownloader downloader;
    private final FailFastFanOut fanOut;

    @Inject
    public BookDownloadService(BookDownloader downloader, FailFastFanOut fanOut) {
        this.downloader = downloader;
        this.fanOut = fanOut;
    }

    public DownloadReceipt download(DownloadRequest request) {
        return downloader.download(requireDownloadable(request));
    }

    public List<DownloadReceipt> downloadBatch(List<DownloadRequest> requests) {
        List<DownloadRequest> validRequests = DownloadRequest.requireValidBatch(requests);

        List<Supplier<DownloadReceipt>> tasks = validRequests.stream()
                .map(this::downloadTask)
                .toList();

        return fanOut.executeAll("download", tasks);
    }

    public DownloadCatalog listDownloads() {
        return new DownloadCatalog(
                downloader.storageDirectory().toString(),
                downloader.listFiles()
        );
    }

    private static DownloadRequest requireDownloadable(DownloadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return request.requireDownloadable();
    }

    private Supplier<DownloadReceipt> downloadTask(DownloadRequest request) {
        return () -> downloader.download(request);
    }
}
