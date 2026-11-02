#!/usr/bin/env bash
set -e

BASE_URL="http://localhost:8080"
GRAFANA_URL="http://localhost:3000/d/virtual-threads-read-for-free"

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

print_header() {
    echo ""
    echo "======================================================================"
    echo "  $1"
    echo "======================================================================"
}

run_real_demo() {
    print_header "Scenario 1: Real Multi-Repository Search & Download"
    echo "Searching live public-domain repositories for 'Frankenstein'..."
    SEARCH_RESP=$(curl -s "${BASE_URL}/api/books/search?q=Frankenstein")

    if command_exists jq; then
        echo "$SEARCH_RESP" | jq '{query, totalResults, sampleBooks: .books[0:3]}'
    else
        echo "$SEARCH_RESP"
    fi

    echo ""
    echo "Downloading the first book found to ./downloads/..."
    if command_exists jq; then
        FIRST_BOOK=$(echo "$SEARCH_RESP" | jq -c '.books[0]')
        if [ "$FIRST_BOOK" != "null" ] && [ -n "$FIRST_BOOK" ]; then
            DOWNLOAD_RESP=$(curl -s -X POST "${BASE_URL}/api/books/download" \
                -H "Content-Type: application/json" \
                -d "$FIRST_BOOK")
            echo "$DOWNLOAD_RESP" | jq .
        else
            echo "No books found to download."
        fi
    fi

    echo ""
    echo "Listing downloaded books on machine:"
    curl -s "${BASE_URL}/api/books/downloads" | (command_exists jq && jq . || cat)
}

run_fail_fast_demo() {
    print_header "Scenario 2: Fail-Fast Concurrency Trigger ('Gatilho de Falha')"
    echo "Triggering search across 3 repositories with 'mock-archive' failing at 150ms..."
    echo "Notice how it returns immediately (~155ms) instead of waiting 850ms for slow siblings!"
    echo ""

    HTTP_CODE=$(curl -s -o /tmp/rff_fail_fast.json -w "%{http_code}" \
        "${BASE_URL}/api/benchmark/search?q=Frankenstein&failRepo=mock-archive")

    echo "HTTP Status: $HTTP_CODE"
    if command_exists jq; then
        jq . /tmp/rff_fail_fast.json
    else
        cat /tmp/rff_fail_fast.json
    fi

    echo ""
    echo "Batch Download Fail-Fast Test (2 slow streaming books + 1 failing book at 150ms):"
    BATCH_PAYLOAD='[
      {
        "title": "Slow Book 1",
        "repository": "mock-gutenberg",
        "format": "txt",
        "downloadUrl": "http://localhost:8089/mock/downloads/slow1.txt"
      },
      {
        "title": "Failing Book",
        "repository": "mock-archive",
        "format": "txt",
        "downloadUrl": "http://localhost:8089/mock/downloads/fail.txt?fail=true"
      },
      {
        "title": "Slow Book 2",
        "repository": "mock-openlibrary",
        "format": "txt",
        "downloadUrl": "http://localhost:8089/mock/downloads/slow2.txt"
      }
    ]'

    BATCH_CODE=$(curl -s -o /tmp/rff_batch_fail.json -w "%{http_code}" \
        -X POST "${BASE_URL}/api/books/download/batch" \
        -H "Content-Type: application/json" \
        -d "$BATCH_PAYLOAD")

    echo "Batch HTTP Status: $BATCH_CODE"
    if command_exists jq; then
        jq . /tmp/rff_batch_fail.json
    else
        cat /tmp/rff_batch_fail.json
    fi

    echo ""
    echo "Check Grafana Tempo at http://localhost:3000/explore to inspect the waterfall trace"
    echo "showing 'cancelled_by_sibling_failure' on the interrupted sibling Virtual Threads!"
}

run_load_demo() {
    print_header "Scenario 3: High-Concurrency Virtual Thread Burst"
    echo "Sending 150 concurrent search requests (spawning 450 Virtual Threads)..."
    echo "Grafana Dashboard: ${GRAFANA_URL}"

    for i in {1..150}; do
        curl -s "${BASE_URL}/api/benchmark/search?q=LoadTest${i}" > /dev/null &
    done

    wait
    echo "Burst complete! Inspect Mimir metrics in Grafana: ${GRAFANA_URL}"
}

case "$1" in
    real)      run_real_demo ;;
    fail-fast) run_fail_fast_demo ;;
    load)      run_load_demo ;;
    all)       run_fail_fast_demo; run_load_demo; run_real_demo ;;
    *)
        echo "Usage: $0 {real|fail-fast|load|all}"
        exit 1
        ;;
esac
