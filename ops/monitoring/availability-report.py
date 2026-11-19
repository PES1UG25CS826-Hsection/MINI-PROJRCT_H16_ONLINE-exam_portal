#!/usr/bin/env python3
"""Summarize a full month's JSONL readiness probes without inventing missing data."""
import argparse
import json
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path


def month_bounds(month: str) -> tuple[datetime, datetime]:
    first = date.fromisoformat(month + "-01")
    if first.month == 12:
        next_month = date(first.year + 1, 1, 1)
    else:
        next_month = date(first.year, first.month + 1, 1)
    return (datetime.combine(first, time.min, timezone.utc), datetime.combine(next_month, time.min, timezone.utc))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("records", type=Path)
    parser.add_argument("month", help="UTC reporting month, YYYY-MM")
    parser.add_argument("--interval", type=int, default=60, help="Configured probe interval in seconds")
    args = parser.parse_args()
    start, end = month_bounds(args.month)
    records = []
    for line_number, line in enumerate(args.records.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        record = json.loads(line)
        timestamp = datetime.fromisoformat(record["checkedAt"].replace("Z", "+00:00")).astimezone(timezone.utc)
        if start <= timestamp < end:
            records.append((timestamp, record))
    records.sort(key=lambda item: item[0])
    expected = int((end - start).total_seconds() / args.interval)
    included = [(timestamp, record) for timestamp, record in records if not record.get("excluded", False)]
    successful = sum(bool(record.get("ok")) for _, record in included)
    availability = successful / len(included) * 100 if included else 0
    largest_gap = max(((right[0] - left[0]).total_seconds() for left, right in zip(records, records[1:])), default=0)
    missing = max(0, expected - len(records))
    print(f"Period (UTC): {start.isoformat()} to {end.isoformat()}")
    print(f"Probe interval: {args.interval}s; expected samples: {expected}; recorded: {len(records)}; missing: {missing}")
    print(f"Excluded maintenance samples: {len(records) - len(included)}")
    print(f"Successful samples: {successful}; failed samples: {len(included) - successful}")
    print(f"Sample availability: {availability:.4f}%")
    print(f"Largest gap between recorded probes: {largest_gap:.0f}s")
    print(f"Meets 99.5% sample target: {'YES' if included and availability >= 99.5 and missing == 0 else 'NO'}")
    if missing:
        print("Note: missing samples are reported and do not count as successful availability.")


if __name__ == "__main__":
    main()
