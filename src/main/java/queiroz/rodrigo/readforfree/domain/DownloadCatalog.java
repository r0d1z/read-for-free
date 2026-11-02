package queiroz.rodrigo.readforfree.domain;

import java.util.List;

/**
 * Read model returned by the download listing endpoint.
 */
public record DownloadCatalog(
        String storageDirectory,
        List<String> files
) {
    public DownloadCatalog {
        storageDirectory = storageDirectory == null ? "" : storageDirectory.trim();
        files = files == null ? List.of() : List.copyOf(files);
    }
}
