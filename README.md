# read-for-free

> Reference implementation and article companion showcasing **Java 25 Virtual Threads**, **Strongly-Typed `CompletableFuture` with Fail-Fast Interruption (`gatilho de falha`)**, and end-to-end observability with **OpenTelemetry**, **Grafana Mimir**, and **Grafana Tempo**.

---

## Layering

```
api/          HTTP only: parameter binding, status codes, JSON
service/      use cases: search fan-out and download orchestration
concurrency/  FailFastFanOut + Virtual Thread helpers
repository/   outbound I/O: public catalogs, WireMock and file storage
domain/       records and domain errors
telemetry/    OpenTelemetry metrics and spans
```

## Core Concurrency Pattern (`FailFastFanOut.java`)

The services own the use cases; the resources only adapt HTTP. Batch search,
benchmark search and batch download all reuse the same coordinator:

```java
// service/BookSearchService.java
public SearchResult search(String query) {
    String term = normalize(query);
    List<Supplier<List<Book>>> tasks = List.of(
            () -> gutendex.search(term),
            () -> openLibrary.search(term),
            () -> internetArchive.search(term));

    return SearchResult.merge(term, fanOut.executeAll("search", tasks));
}
```

```java
// api/BookResource.java
@GET
@Path("/search")
@RunOnVirtualThread
public SearchResult search(@QueryParam("q") @DefaultValue(BookSearchService.DEFAULT_QUERY) String query) {
    return searchService.search(query);
}
```

`FailFastFanOut` implements the actual fail-fast trigger: standard
`CompletableFuture.allOf(...)` waits for slow tasks even when one fails early,
so the coordinator calls `all.completeExceptionally(ex)` and
`executor.shutdownNow()` to unblock the caller and interrupt the sibling
Virtual Threads.

### Key Highlights for the Article
1. **Idiomatic Virtual Threads (`@RunOnVirtualThread`):** Request handling and blocking `HttpClient` calls execute on lightweight Virtual Threads without reactive boilerplate.
2. **Strongly-Typed `CompletableFuture` + Fail-Fast Interruption:** `FailFastFanOut` isolates the pattern so every use case (search, benchmark, batch download) gets the same guarantee: one failure cancels the siblings instead of waiting for the slowest task.
3. **Static Factory Methods (`VirtualThreads.newTracedExecutor()`, `SearchResult.merge(...)`, `Book.of(...)`):** Keeps domain construction and OpenTelemetry `Context` propagation (`Context.current().wrap(...)`) concise and readable.
4. **Observability in Mimir & Tempo:** Plots active Virtual Threads (`books_vt_active`) against OS Platform Threads (`jvm_thread_count`) in **Grafana Mimir**, alongside distributed waterfall traces with `"cancelled_by_sibling_failure"` span events in **Grafana Tempo**.

---

## Quickstart

### 1. Start Observability & Benchmark Stack
```bash
docker compose up -d
```
- **Grafana Dashboard:** [http://localhost:3000/d/virtual-threads-read-for-free](http://localhost:3000/d/virtual-threads-read-for-free)
- **Tempo Traces:** [http://localhost:3000/explore](http://localhost:3000/explore) (Datasource: `Tempo`)
- **Mimir Prometheus API:** `http://localhost:9009/prometheus`
- **WireMock Benchmark Server:** `http://localhost:8089/__admin`

### 2. Run Quarkus Application (Java 25)
```bash
./mvnw quarkus:dev
```

### 3. Run Article Demonstration Scenarios
```bash
# 1. Search real public-domain catalogs (Gutendex, Open Library, Internet Archive) & download an ebook
./scripts/article-demos.sh real

# 2. Trigger deterministic fail-fast cancellation & mid-stream download interruption
./scripts/article-demos.sh fail-fast

# 3. Fire 150 concurrent requests (450 Virtual Threads) to populate Grafana Mimir charts
./scripts/article-demos.sh load
```

---

## REST Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/books/search?q={query}` | Parallel search across Gutendex, Open Library, and Internet Archive |
| `POST` | `/api/books/download` | Streams a book to `./downloads/` on a Virtual Thread |
| `POST` | `/api/books/download/batch` | Concurrently streams a JSON array of books with fail-fast cancellation |
| `GET` | `/api/books/downloads` | Lists books saved in `./downloads/` |
| `GET` | `/api/benchmark/search?q={query}&failRepo={optional}` | High-concurrency & chaos benchmark endpoint backed by WireMock |

---

## Automated Tests

```bash
./mvnw clean test
```
