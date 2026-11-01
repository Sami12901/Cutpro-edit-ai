"""
CutPro Base OpaqueBoxTestCase
"""

import time
import traceback
from typing import Callable, Optional
from .reporter import TestResult


class OpaqueBoxTestCase:
    def __init__(self, test_id: str, feature_id: str, tier: int, name: str, test_func: Callable[[], None]):
        self.test_id = test_id
        self.feature_id = feature_id
        self.tier = tier
        self.name = name
        self.test_func = test_func

    def setUp(self):
        pass

    def tearDown(self):
        pass

    def run(self) -> TestResult:
        self.setUp()
        start = time.perf_counter()
        status = "PASS"
        error_msg: Optional[str] = None
        stack_str: Optional[str] = None

        try:
            self.test_func()
        except Exception as e:
            status = "FAIL"
            error_msg = str(e)
            stack_str = traceback.format_exc()
        finally:
            elapsed_ms = (time.perf_counter() - start) * 1000.0
            try:
                self.tearDown()
            except Exception as td_e:
                if status == "PASS":
                    status = "FAIL"
                    error_msg = f"tearDown failed: {td_e}"
                    stack_str = traceback.format_exc()

        return TestResult(
            test_id=self.test_id,
            feature_id=self.feature_id,
            tier=self.tier,
            name=self.name,
            status=status,
            duration_ms=elapsed_ms,
            error_message=error_msg,
            stack_trace=stack_str
        )
