#!/usr/bin/env python3
"""Record periodic HTTP readiness checks as JSON Lines for availability reporting."""
import argparse
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


def probe(url: str, timeout: float) -> dict:
    checked_at = datetime.now(timezone.utc).isoformat()
    started = time.monotonic()
    status = None
    error = None
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "online-exam-availability-probe/1.0"})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
            response.read(4096)
    except urllib.error.HTTPError as exc:
        status = exc.code
        error = str(exc)
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        error = str(exc)
    return {
        "checkedAt": checked_at,
        "ok": status is not None and 200 <= status < 400,
        "status": status,
        "durationMs": round((time.monotonic() - started) * 1000, 2),
        "error": error,
        "excluded": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="Public load balancer readiness URL, for example https://exam.example.com/actuator/health/readiness")
    parser.add_argument("--interval", type=float, default=60, help="Seconds between probes (default: 60)")
    parser.add_argument("--timeout", type=float, default=5, help="HTTP timeout in seconds (default: 5)")
    parser.add_argument("--output", type=Path, default=Path("availability.jsonl"))
    args = parser.parse_args()
    if args.interval <= 0 or args.timeout <= 0:
        parser.error("interval and timeout must be positive")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    while True:
        record = probe(args.url, args.timeout)
        with args.output.open("a", encoding="utf-8") as output:
            output.write(json.dumps(record, separators=(",", ":")) + "\n")
            output.flush()
        print(json.dumps(record), flush=True)
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
