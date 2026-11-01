#!/usr/bin/env bash
# CutPro E2E Automated Test Suite Shell Wrapper
# Zero emojis, clean POSIX compliance

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

cd "${PROJECT_ROOT}"

echo "Executing CutPro Opaque-Box E2E Tests..."
python3 tests/e2e/run_tests.py "$@"
