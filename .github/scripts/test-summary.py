#!/usr/bin/env python3
"""Writes what a test job did onto the workflow run page.

Gradle leaves a JUnit XML file per test class under build/test-results/<task>/, which is the same
shape for all four targets - the JVM suite, the browser one karma drives, the Android host tests and
the iOS simulator. This reads them, adds them up, and appends a few lines to $GITHUB_STEP_SUMMARY,
so a run says how many tests it ran without anyone opening a log or downloading an artifact.

It reports and nothing more: the exit code is always 0, because the Gradle step has already failed
the job if anything failed, and a reporting step that fails as well only obscures which one mattered.
Run without $GITHUB_STEP_SUMMARY set, it prints to stdout, which is how to see what it would write.
"""

import argparse
import csv
import glob
import os
import sys
import xml.etree.ElementTree as ET

# A very long list helps nobody; the artifact holds the full results.
MAX_LISTED_FAILURES = 25


def suites(directory):
    """Every <testsuite> under `directory`, whether or not a <testsuites> wraps it."""
    pattern = os.path.join(directory, "**", "*.xml")
    for path in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            # A run killed part way through leaves a half-written file. One unreadable file is not
            # a reason to report nothing at all about the ones that are fine.
            print(f"skipping unreadable {path}", file=sys.stderr)
            continue
        if root.tag == "testsuite":
            yield root
        else:
            yield from root.iter("testsuite")


def failing_cases(suite):
    """The name and first line of the message of every case that failed or errored."""
    for case in suite.iter("testcase"):
        for bad in list(case.findall("failure")) + list(case.findall("error")):
            name = f"{case.get('classname', '')}.{case.get('name', '')}".strip(".")
            message = (bad.get("message") or bad.get("type") or "").strip()
            yield name, message.splitlines()[0] if message else ""


def coverage_percentages(csv_path):
    """Instruction and branch coverage from JaCoCo's CSV, the same sums the badge is made from."""
    totals = {"IC": 0, "IM": 0, "BC": 0, "BM": 0}
    with open(csv_path, newline="") as handle:
        for row in csv.DictReader(handle):
            totals["IC"] += int(row["INSTRUCTION_COVERED"])
            totals["IM"] += int(row["INSTRUCTION_MISSED"])
            totals["BC"] += int(row["BRANCH_COVERED"])
            totals["BM"] += int(row["BRANCH_MISSED"])

    def percent(covered, missed):
        total = covered + missed
        return None if total == 0 else 100.0 * covered / total

    return percent(totals["IC"], totals["IM"]), percent(totals["BC"], totals["BM"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", required=True, help="heading for this job's section")
    parser.add_argument("--results", required=True, help="directory holding the JUnit XML")
    parser.add_argument("--jacoco-csv", help="optional JaCoCo CSV to report coverage from")
    args = parser.parse_args()

    tests = failures = errors = skipped = 0
    seconds = 0.0
    listed = []
    for suite in suites(args.results):
        tests += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        seconds += float(suite.get("time", 0) or 0)
        listed.extend(failing_cases(suite))

    lines = [f"## {args.title}", ""]

    if tests == 0:
        # Not the same as everything passing: no results at all usually means the build did not get
        # as far as running them, and a summary reading "0 tests" would look like a clean run.
        lines.append(f"No test results under `{args.results}` — the tests did not get as far as running.")
    else:
        bad = failures + errors
        parts = [f"**{tests} tests**"]
        parts.append("all passing" if bad == 0 else f"**{bad} failed**")
        if skipped:
            parts.append(f"{skipped} skipped")
        lines.append(", ".join(parts) + f" — {seconds:.1f}s")

        if listed:
            lines += ["", "<details><summary>What failed</summary>", ""]
            for name, message in listed[:MAX_LISTED_FAILURES]:
                lines.append(f"- `{name}`" + (f" — {message}" if message else ""))
            if len(listed) > MAX_LISTED_FAILURES:
                lines.append(f"- …and {len(listed) - MAX_LISTED_FAILURES} more")
            lines += ["", "</details>"]

    if args.jacoco_csv and os.path.exists(args.jacoco_csv):
        instructions, branches = coverage_percentages(args.jacoco_csv)
        if instructions is not None:
            covered = f"{instructions:.1f}% of instructions"
            if branches is not None:
                covered += f", {branches:.1f}% of branches"
            lines += ["", f"Covered: {covered}."]

    text = "\n".join(lines) + "\n"
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as handle:
            handle.write(text)
    else:
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
