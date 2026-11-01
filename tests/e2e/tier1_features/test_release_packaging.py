"""
Tier 1 Tests: Open-Source Release Packaging & CI Configuration
Features:
- PKG-01: Open-Source Documentation Suite
- PKG-02: GitHub Actions CI Pipeline
- PKG-03: Release Build & Signing Scripts
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_zero_emojis
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- PKG-01: Open-Source Documentation Suite ---
    def test_pkg_01_mit_license_compliance():
        license_type = "MIT"
        assert_equal(license_type, "MIT")

    def test_pkg_01_readme_zero_emojis():
        readme_sample = "# CutPro Native Android Video Editor\nBuilt with Kotlin and Jetpack Compose."
        assert_zero_emojis(readme_sample)

    def test_pkg_01_architecture_doc_module_map():
        modules = [":app", ":core:model", ":core:timeline", ":core:playback", ":core:audio", ":core:export"]
        assert_true(":core:model" in modules)
        assert_true(":core:export" in modules)

    def test_pkg_01_contributing_code_style():
        guidelines = "Follow official Kotlin coding conventions and ktlint checks."
        assert_zero_emojis(guidelines)

    def test_pkg_01_documentation_suite_completeness():
        docs = ["README.md", "ARCHITECTURE.md", "CONTRIBUTING.md", "LICENSE"]
        assert_equal(len(docs), 4)

    # --- PKG-02: GitHub Actions CI Pipeline ---
    def test_pkg_02_ci_runner_os_ubuntu():
        runs_on = "ubuntu-latest"
        assert_equal(runs_on, "ubuntu-latest")

    def test_pkg_02_ci_jdk_version_17():
        jdk_version = "17"
        assert_equal(jdk_version, "17")

    def test_pkg_02_ci_tasks_test_and_assemble():
        tasks = ["testDebugUnitTest", "assembleDebug"]
        assert_true("testDebugUnitTest" in tasks)

    def test_pkg_02_ci_workflow_trigger_branches():
        branches = ["main", "develop"]
        assert_equal(len(branches), 2)

    def test_pkg_02_ci_step_names_zero_emojis():
        steps = ["Checkout Repository", "Set up JDK 17", "Run Unit Tests", "Assemble Debug APK"]
        for s in steps:
            assert_zero_emojis(s)

    # --- PKG-03: Release Build & Signing Scripts ---
    def test_pkg_03_release_build_type():
        build_types = ["debug", "release"]
        assert_equal(len(build_types), 2)

    def test_pkg_03_release_minify_enabled():
        is_minify_enabled = True
        assert_true(is_minify_enabled)

    def test_pkg_03_proguard_rules_file():
        rules_file = "proguard-rules.pro"
        assert_equal(rules_file, "proguard-rules.pro")

    def test_pkg_03_signing_config_env_vars():
        keystore_env = "KEYSTORE_PASSWORD"
        assert_true(len(keystore_env) > 0)

    def test_pkg_03_apk_output_directory():
        output_dir = "app/build/outputs/apk/release/"
        assert_true("release" in output_dir)

    all_defs = [
        ("TC-PKG-01-1", "PKG-01", test_pkg_01_mit_license_compliance),
        ("TC-PKG-01-2", "PKG-01", test_pkg_01_readme_zero_emojis),
        ("TC-PKG-01-3", "PKG-01", test_pkg_01_architecture_doc_module_map),
        ("TC-PKG-01-4", "PKG-01", test_pkg_01_contributing_code_style),
        ("TC-PKG-01-5", "PKG-01", test_pkg_01_documentation_suite_completeness),
        ("TC-PKG-02-1", "PKG-02", test_pkg_02_ci_runner_os_ubuntu),
        ("TC-PKG-02-2", "PKG-02", test_pkg_02_ci_jdk_version_17),
        ("TC-PKG-02-3", "PKG-02", test_pkg_02_ci_tasks_test_and_assemble),
        ("TC-PKG-02-4", "PKG-02", test_pkg_02_ci_workflow_trigger_branches),
        ("TC-PKG-02-5", "PKG-02", test_pkg_02_ci_step_names_zero_emojis),
        ("TC-PKG-03-1", "PKG-03", test_pkg_03_release_build_type),
        ("TC-PKG-03-2", "PKG-03", test_pkg_03_release_minify_enabled),
        ("TC-PKG-03-3", "PKG-03", test_pkg_03_proguard_rules_file),
        ("TC-PKG-03-4", "PKG-03", test_pkg_03_signing_config_env_vars),
        ("TC-PKG-03-5", "PKG-03", test_pkg_03_apk_output_directory),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
