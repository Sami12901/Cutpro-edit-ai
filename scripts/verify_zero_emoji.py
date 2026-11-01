#!/usr/bin/env python3
"""
CutPro Zero-Emoji Mandate Verifier
Scans all source code, manifests, documentation, tests, and configuration files
to ensure zero Unicode emojis across the entire project repository.
"""

import os
import sys
import re

EMOJI_PATTERN = re.compile(
    "["
    "\U0001F600-\U0001F64F"  # emoticons
    "\U0001F300-\U0001F5FF"  # symbols & pictographs
    "\U0001F680-\U0001F6FF"  # transport & map symbols
    "\U0001F1E0-\U0001F1FF"  # flags (iOS)
    "\U00002702-\U000027B0"  # dingbats
    "\U000024C2"              # circled M
    "\U0001F200-\U0001F251"  # enclosed ideographic
    "\U0001F900-\U0001F9FF"  # supplemental symbols and pictographs
    "\U0001FA70-\U0001FAFF"  # symbols and pictographs extended-a
    "]+",
    flags=re.UNICODE
)

EXCLUDED_DIRS = {
    ".git",
    ".gradle",
    "build",
    ".idea",
    ".agents",
    "__pycache__",
    ".pytest_cache"
}

BINARY_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico",
    ".apk", ".aab", ".jar", ".aar", ".class", ".dex",
    ".so", ".dylib", ".dll", ".exe",
    ".mp4", ".mov", ".avi", ".mkv", ".mp3", ".wav", ".aac",
    ".zip", ".tar", ".gz", ".7z",
    ".pdf", ".bin"
}


def scan_file(filepath: str):
    violations = []
    try:
        with open(filepath, "r", encoding="utf-8", errors="replace") as f:
            for line_no, line in enumerate(f, 1):
                matches = EMOJI_PATTERN.findall(line)
                if matches:
                    violations.append((line_no, matches, line.strip()))
    except Exception as e:
        # If file cannot be read as text, skip it
        pass
    return violations


def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    print(f"Verifying Zero-Emoji Invariant across repository: {root_dir}")
    print("=" * 80)

    total_files = 0
    total_violations = 0
    violation_details = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Filter directories in-place
        dirnames[:] = [d for d in dirnames if d not in EXCLUDED_DIRS and not any(part in EXCLUDED_DIRS for part in os.path.relpath(os.path.join(dirpath, d), root_dir).split(os.sep))]

        rel_dir = os.path.relpath(dirpath, root_dir)
        if any(part in EXCLUDED_DIRS for part in rel_dir.split(os.sep)):
            continue

        for fname in filenames:
            ext = os.path.splitext(fname)[1].lower()
            if ext in BINARY_EXTENSIONS:
                continue

            filepath = os.path.join(dirpath, fname)
            rel_path = os.path.relpath(filepath, root_dir)
            total_files += 1

            v = scan_file(filepath)
            if v:
                total_violations += len(v)
                violation_details.append((rel_path, v))

    print(f"Total files scanned: {total_files}")
    if total_violations == 0:
        print("ZERO-EMOJI INVARIANT VERIFIED: 0 emoji violations found across all files.")
        print("=" * 80)
        sys.exit(0)
    else:
        print(f"ZERO-EMOJI INVARIANT VIOLATED: {total_violations} violations found!")
        print("=" * 80)
        for rel_path, issues in violation_details:
            print(f"File: {rel_path} ({len(issues)} violations)")
            for line_no, matches, line in issues[:5]:
                print(f"  Line {line_no}: {[m.encode('unicode_escape').decode('ascii') for m in matches]}")
        sys.exit(1)


if __name__ == "__main__":
    main()
