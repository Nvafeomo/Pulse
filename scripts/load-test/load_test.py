#!/usr/bin/env python3
"""
Load test for the Pulse API. Measures how many notifications per minute the
API accepts and, optionally, how many the email consumer actually delivers.

Uses only the Python standard library, so there is nothing to install.

Phase 1 (accepted/min): worker threads send POST /api/v1/notifications as
fast as they can for --duration seconds and count the 202 responses.

Phase 2 (processed/min, with --wait-for-delivery): polls the list endpoint
until every job sent in phase 1 is DELIVERED (or --timeout passes), then
divides the delivered count by the time from the first request to the last
delivery. Run lambda/email-consumer/local_poller.py alongside it so jobs
actually get processed.

Example:
    python scripts/load-test/load_test.py --duration 60 --concurrency 16 --wait-for-delivery
"""

import argparse
import http.client
import json
import statistics
import threading
import time
from urllib.parse import urlparse


def send_requests(base, api_key, deadline, results, lock, counter):
    url = urlparse(base)
    conn = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=10)
    headers = {"Content-Type": "application/json", "X-API-Key": api_key}
    latencies, accepted, errors = [], 0, 0

    while time.monotonic() < deadline:
        with lock:
            counter[0] += 1
            n = counter[0]
        body = json.dumps({
            "channels": ["EMAIL"],
            "subject": f"Load test #{n}",
            "body": "Pulse load test message",
            "recipientEmail": f"load-test-{n}@example.com",
        })
        start = time.monotonic()
        try:
            conn.request("POST", "/api/v1/notifications", body=body, headers=headers)
            response = conn.getresponse()
            response.read()
            if response.status == 202:
                accepted += 1
                latencies.append(time.monotonic() - start)
            else:
                errors += 1
        except (OSError, http.client.HTTPException):
            errors += 1
            conn.close()
            conn = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=10)

    conn.close()
    with lock:
        results["accepted"] += accepted
        results["errors"] += errors
        results["latencies"].extend(latencies)


def delivered_count(base, api_key):
    url = urlparse(base)
    conn = http.client.HTTPConnection(url.hostname, url.port or 80, timeout=10)
    conn.request(
        "GET",
        "/api/v1/notifications?status=DELIVERED&size=1",
        headers={"X-API-Key": api_key},
    )
    response = conn.getresponse()
    data = json.loads(response.read())
    conn.close()
    if response.status != 200:
        raise SystemExit(f"List endpoint returned {response.status}: {data}")
    return data["totalElements"]


def percentile(values, pct):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, round(pct / 100 * (len(ordered) - 1)))
    return ordered[index]


def main():
    parser = argparse.ArgumentParser(description="Pulse API load test")
    parser.add_argument("--url", default="http://localhost:8080", help="API base URL")
    parser.add_argument("--api-key", default="pulse-dev-key-local", help="X-API-Key value")
    parser.add_argument("--duration", type=int, default=60, help="seconds to send for (default 60)")
    parser.add_argument("--concurrency", type=int, default=16, help="sender threads (default 16)")
    parser.add_argument("--wait-for-delivery", action="store_true",
                        help="also measure delivered/min (needs the email consumer running)")
    parser.add_argument("--timeout", type=int, default=300,
                        help="max seconds to wait for delivery (default 300)")
    args = parser.parse_args()

    baseline = delivered_count(args.url, args.api_key) if args.wait_for_delivery else 0

    print(f"Sending for {args.duration}s with {args.concurrency} threads against {args.url} ...")
    results = {"accepted": 0, "errors": 0, "latencies": []}
    lock, counter = threading.Lock(), [0]
    started = time.monotonic()
    deadline = started + args.duration
    threads = [
        threading.Thread(target=send_requests,
                         args=(args.url, args.api_key, deadline, results, lock, counter))
        for _ in range(args.concurrency)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    send_seconds = time.monotonic() - started

    accepted = results["accepted"]
    latencies_ms = [s * 1000 for s in results["latencies"]]
    print("\n── API (accepted) ──────────────────────────────")
    print(f"accepted:        {accepted} in {send_seconds:.1f}s")
    print(f"errors:          {results['errors']}")
    print(f"accepted/min:    {accepted / send_seconds * 60:,.0f}")
    if latencies_ms:
        print(f"latency p50:     {statistics.median(latencies_ms):.0f} ms")
        print(f"latency p95:     {percentile(latencies_ms, 95):.0f} ms")
        print(f"latency p99:     {percentile(latencies_ms, 99):.0f} ms")

    if not args.wait_for_delivery or accepted == 0:
        return

    print("\nWaiting for the email consumer to deliver every job ...")
    target = baseline + accepted
    delivered = baseline
    last_progress = time.monotonic()
    while time.monotonic() - started < args.duration + args.timeout:
        current = delivered_count(args.url, args.api_key)
        if current > delivered:
            delivered, last_progress = current, time.monotonic()
        if delivered >= target:
            break
        time.sleep(2)

    processed = delivered - baseline
    elapsed = last_progress - started
    print("\n── End to end (delivered) ──────────────────────")
    print(f"delivered:       {processed} of {accepted}")
    print(f"elapsed:         {elapsed:.1f}s (first request to last delivery)")
    print(f"delivered/min:   {processed / elapsed * 60:,.0f}")
    if processed < accepted:
        print("Not every job was delivered before the timeout. Is local_poller.py running?")


if __name__ == "__main__":
    main()
