package queiroz.rodrigo.readforfree.api;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

@QuarkusTest
class BookResourceTest {

    @Test
    @DisplayName("GET /api/books/downloads should return storage directory and files list")
    void testDownloadsEndpoint() {
        given()
                .when().get("/api/books/downloads")
                .then()
                .statusCode(200)
                .body("storageDirectory", notNullValue())
                .body("files", notNullValue());
    }

    @Test
    @DisplayName("POST /api/books/download without downloadUrl should return 400")
    void testInvalidSingleDownload() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"title\": \"Missing URL\"}")
                .when().post("/api/books/download")
                .then()
                .statusCode(400)
                .body("error", equalTo("downloadUrl is required"));
    }

    @Test
    @DisplayName("POST /api/books/download/batch with empty array should return 400")
    void testEmptyBatchDownload() {
        given()
                .contentType(ContentType.JSON)
                .body("[]")
                .when().post("/api/books/download/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("books list cannot be empty"));
    }

    @Test
    @DisplayName("POST /api/books/download/batch with an invalid request should return 400 before starting downloads")
    void testInvalidBatchDownload() {
        given()
                .contentType(ContentType.JSON)
                .body("[{\"title\": \"Missing URL\"}]")
                .when().post("/api/books/download/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("downloadUrl is required"));
    }

    @Test
    @DisplayName("POST /api/books/download/batch with malformed or non-HTTP URL should return 400 instead of 502")
    void testMalformedBatchUrlReturns400() {
        given()
                .contentType(ContentType.JSON)
                .body("[{\"title\": \"Bad Scheme\", \"downloadUrl\": \"file:///etc/passwd\"}]")
                .when().post("/api/books/download/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("downloadUrl must be a valid HTTP(S) URI"));
    }

    @Test
    @DisplayName("GET /api/benchmark/search with failRepo should return 502 FailFastError")
    void testFailFastExceptionMapping() {
        given()
                .queryParam("q", "Frankenstein")
                .queryParam("failRepo", "mock-archive")
                .when().get("/api/benchmark/search")
                .then()
                .statusCode(502)
                .body("error", equalTo("Fail-Fast Circuit Tripped"))
                .body("failedRepository", notNullValue())
                .body("failFastCancelledSiblings", equalTo(true));
    }
}
