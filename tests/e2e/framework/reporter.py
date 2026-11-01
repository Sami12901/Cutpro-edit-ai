"""
CutPro Test Reporter & Structured Output Engine
Zero emojis, clean ASCII formatting, JSON and JUnit XML reporting.
"""

import os
import json
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class TestResult:
    test_id: str
    feature_id: str
    tier: int
    name: str
    status: str  # "PASS", "FAIL", "SKIP"
    duration_ms: float
    error_message: Optional[str] = None
    stack_trace: Optional[str] = None


class TestReporter:
    def __init__(self, output_dir: str):
        self.output_dir = output_dir
        self.results: List[TestResult] = []
        self.start_time: float = 0.0
        self.end_time: float = 0.0

    def start_suite(self):
        self.start_time = time.time()
        print("=" * 80)
        print("CUTPRO OPAQUE-BOX END-TO-END TEST SUITE")
        print("Test Philosophy: Opaque-box, requirement-driven, zero emojis")
        print("=" * 80)

    def record_result(self, result: TestResult):
        self.results.append(result)
        status_tag = f"[{result.status}]"
        print(f"{status_tag:<8} Tier {result.tier} | {result.feature_id:<14} | {result.name} ({result.duration_ms:.2f}ms)")
        if result.status == "FAIL" and result.error_message:
            print(f"         Error: {result.error_message}")

    def end_suite(self):
        self.end_time = time.time()
        total_time = self.end_time - self.start_time
        total_tests = len(self.results)
        passed = sum(1 for r in self.results if r.status == "PASS")
        failed = sum(1 for r in self.results if r.status == "FAIL")
        skipped = sum(1 for r in self.results if r.status == "SKIP")

        tier_counts = {}
        for r in self.results:
            t = r.tier
            tier_counts[t] = tier_counts.get(t, {"PASS": 0, "FAIL": 0, "TOTAL": 0})
            tier_counts[t]["TOTAL"] += 1
            if r.status in ("PASS", "FAIL"):
                tier_counts[t][r.status] += 1

        print("\n" + "=" * 80)
        print("TEST SUITE EXECUTION SUMMARY")
        print("=" * 80)
        for t in sorted(tier_counts.keys()):
            tc = tier_counts[t]
            print(f"Tier {t}: Total = {tc['TOTAL']:<4} | Passed = {tc['PASS']:<4} | Failed = {tc['FAIL']:<4}")
        print("-" * 80)
        print(f"Grand Total:  {total_tests} tests executed in {total_time:.2f} seconds")
        print(f"Status:       Passed: {passed} | Failed: {failed} | Skipped: {skipped}")
        print("Verdict:      " + ("ALL TESTS PASSED" if failed == 0 else "FAILURES DETECTED"))
        print("=" * 80)

        os.makedirs(self.output_dir, exist_ok=True)
        self._write_json_report(total_time, total_tests, passed, failed, skipped, tier_counts)
        self._write_junit_xml(total_time, total_tests, passed, failed, skipped)

    def _write_json_report(self, total_time, total, passed, failed, skipped, tier_counts):
        json_path = os.path.join(self.output_dir, "e2e_results.json")
        data = {
            "suiteName": "CutPro Opaque-Box E2E Suite",
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "totalDurationSec": round(total_time, 3),
            "summary": {
                "total": total,
                "passed": passed,
                "failed": failed,
                "skipped": skipped,
                "passRate": round((passed / total * 100.0) if total > 0 else 0.0, 2)
            },
            "tierSummary": tier_counts,
            "testCases": [
                {
                    "testId": r.test_id,
                    "featureId": r.feature_id,
                    "tier": r.tier,
                    "name": r.name,
                    "status": r.status,
                    "durationMs": round(r.duration_ms, 2),
                    "error": r.error_message
                }
                for r in self.results
            ]
        }
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
        print(f"JSON Report written to: {json_path}")

    def _write_junit_xml(self, total_time, total, passed, failed, skipped):
        xml_path = os.path.join(self.output_dir, "junit_report.xml")
        root = ET.Element("testsuite", {
            "name": "CutPro_E2E_Tests",
            "tests": str(total),
            "failures": str(failed),
            "skipped": str(skipped),
            "errors": "0",
            "time": f"{total_time:.3f}"
        })

        for r in self.results:
            tc_elem = ET.SubElement(root, "testcase", {
                "classname": f"CutPro.Tier{r.tier}.{r.feature_id}",
                "name": r.name,
                "time": f"{r.duration_ms / 1000.0:.4f}"
            })
            if r.status == "FAIL":
                fail_elem = ET.SubElement(tc_elem, "failure", {"message": r.error_message or "Assertion failed"})
                if r.stack_trace:
                    fail_elem.text = r.stack_trace
            elif r.status == "SKIP":
                ET.SubElement(tc_elem, "skipped")

        tree = ET.ElementTree(root)
        tree.write(xml_path, encoding="utf-8", xml_declaration=True)
        print(f"JUnit XML Report written to: {xml_path}")
