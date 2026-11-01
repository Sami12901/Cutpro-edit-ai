"""
CutPro Opaque-Box E2E Test Suite Runner
Discovers, organizes, and executes all Tier 1, Tier 2, Tier 3, and Tier 4 test cases.
Enforces the Zero-Emoji Mandate and produces structured JSON and JUnit XML reports.
"""

import sys
import os
import argparse
import re
from typing import List, Optional

# Add the project root to sys.path so tests can be imported cleanly
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, "..", ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from tests.e2e.framework.test_case import OpaqueBoxTestCase
from tests.e2e.framework.reporter import TestReporter

# Import all Tier 1 Feature test modules
from tests.e2e.tier1_features import (
    test_core_architecture,
    test_persistence,
    test_security,
    test_media_import,
    test_ui_spec,
    test_timeline_engine,
    test_playback_engine,
    test_audio_engine,
    test_motion_graphics,
    test_templates,
    test_sync_engine,
    test_subtitles,
    test_ai_subsystem,
    test_export_pipeline,
    test_release_packaging,
    test_v2_features
)

# Import all Tier 2 Boundary test modules
from tests.e2e.tier2_boundaries import (
    test_boundary_core,
    test_boundary_security,
    test_boundary_ui,
    test_boundary_timeline,
    test_boundary_audio,
    test_boundary_motion,
    test_boundary_subtitles,
    test_boundary_export,
    test_boundary_packaging
)

# Import all Tier 3 Combinations test modules
from tests.e2e.tier3_combinations import (
    test_pairwise_timeline_audio,
    test_pairwise_motion_export,
    test_pairwise_subtitles_ai,
    test_pairwise_storage_bundle
)

# Import all Tier 4 Production Scenario test modules
from tests.e2e.tier4_scenarios import (
    test_scenario_youtube_review,
    test_scenario_tiktok_reels,
    test_scenario_cinematic_doc,
    test_scenario_product_promo,
    test_scenario_podcast_workflow
)


def collect_all_tests() -> List[OpaqueBoxTestCase]:
    """Collects and aggregates test cases from all test modules across Tiers 1-4."""
    all_tests: List[OpaqueBoxTestCase] = []

    # Tier 1 Modules
    tier1_modules = [
        test_core_architecture,
        test_persistence,
        test_security,
        test_media_import,
        test_ui_spec,
        test_timeline_engine,
        test_playback_engine,
        test_audio_engine,
        test_motion_graphics,
        test_templates,
        test_sync_engine,
        test_subtitles,
        test_ai_subsystem,
        test_export_pipeline,
        test_release_packaging,
        test_v2_features
    ]
    for mod in tier1_modules:
        if hasattr(mod, "register_tests"):
            all_tests.extend(mod.register_tests())

    # Tier 2 Modules
    tier2_modules = [
        test_boundary_core,
        test_boundary_security,
        test_boundary_ui,
        test_boundary_timeline,
        test_boundary_audio,
        test_boundary_motion,
        test_boundary_subtitles,
        test_boundary_export,
        test_boundary_packaging
    ]
    for mod in tier2_modules:
        if hasattr(mod, "register_tests"):
            all_tests.extend(mod.register_tests())

    # Tier 3 Modules
    tier3_modules = [
        test_pairwise_timeline_audio,
        test_pairwise_motion_export,
        test_pairwise_subtitles_ai,
        test_pairwise_storage_bundle
    ]
    for mod in tier3_modules:
        if hasattr(mod, "register_tests"):
            all_tests.extend(mod.register_tests())

    # Tier 4 Modules
    tier4_modules = [
        test_scenario_youtube_review,
        test_scenario_tiktok_reels,
        test_scenario_cinematic_doc,
        test_scenario_product_promo,
        test_scenario_podcast_workflow
    ]
    for mod in tier4_modules:
        if hasattr(mod, "register_tests"):
            all_tests.extend(mod.register_tests())

    return all_tests


def run_e2e_suite(
    tier_filter: Optional[int] = None,
    name_filter: Optional[str] = None,
    reports_dir: Optional[str] = None,
    verbose: bool = False
) -> int:
    """
    Executes the collected test suite, filtered by tier or name.
    Outputs structured reports to reports_dir.
    Returns 0 on complete pass, 1 if any failure occurs.
    """
    if reports_dir is None:
        reports_dir = os.path.join(CURRENT_DIR, "reports")

    all_tests = collect_all_tests()
    selected_tests: List[OpaqueBoxTestCase] = []

    for t in all_tests:
        if tier_filter is not None and t.tier != tier_filter:
            continue
        if name_filter:
            search_str = f"{t.test_id} {t.feature_id} {t.name}"
            if not re.search(name_filter, search_str, re.IGNORECASE):
                continue
        selected_tests.append(t)

    reporter = TestReporter(output_dir=reports_dir)
    reporter.start_suite()

    print(f"Total discovered tests: {len(all_tests)}")
    print(f"Selected tests to run:  {len(selected_tests)}")
    if tier_filter:
        print(f"Filtering by Tier:      {tier_filter}")
    if name_filter:
        print(f"Filtering by Pattern:   {name_filter}")
    print("-" * 80)

    for idx, test_case in enumerate(selected_tests, 1):
        result = test_case.run()
        reporter.record_result(result)
        if verbose and result.status == "FAIL" and result.stack_trace:
            print(result.stack_trace)

    reporter.end_suite()

    has_failures = any(r.status == "FAIL" for r in reporter.results)
    return 1 if has_failures else 0


def main():
    parser = argparse.ArgumentParser(
        description="CutPro Opaque-Box E2E Headless Test Runner (Zero Emojis)"
    )
    parser.add_argument(
        "--tier",
        type=int,
        choices=[1, 2, 3, 4],
        help="Run only tests from a specific Tier (1, 2, 3, or 4)"
    )
    parser.add_argument(
        "--filter",
        type=str,
        help="Regex or substring filter matching test ID, feature ID, or name"
    )
    parser.add_argument(
        "--reports-dir",
        type=str,
        default=os.path.join(CURRENT_DIR, "reports"),
        help="Output directory for e2e_results.json and junit_report.xml"
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print verbose failure stack traces"
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Explicitly run all test tiers"
    )

    args = parser.parse_args()

    exit_code = run_e2e_suite(
        tier_filter=args.tier,
        name_filter=args.filter,
        reports_dir=args.reports_dir,
        verbose=args.verbose
    )
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
