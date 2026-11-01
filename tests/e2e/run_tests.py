#!/usr/bin/env python3
"""
CutPro Opaque-Box E2E Test Suite CLI Launcher
Execute all tests:
    python tests/e2e/run_tests.py --all

Execute a single tier:
    python tests/e2e/run_tests.py --tier 1
    python tests/e2e/run_tests.py --tier 2
    python tests/e2e/run_tests.py --tier 3
    python tests/e2e/run_tests.py --tier 4

Filter by feature:
    python tests/e2e/run_tests.py --filter FEAT-MG-001
"""

import sys
import os

CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from tests.e2e.runner import main

if __name__ == "__main__":
    main()
