"""
CutPro Opaque-Box Assertion Utilities
Provides precise assertions for numerical tolerances, microsecond timecodes,
and zero-emoji compliance.
"""

import math
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


class AssertionError(Exception):
    pass


def assert_true(condition: bool, message: str = "Expected condition to be True"):
    if not condition:
        raise AssertionError(f"Assertion failed: {message}")


def assert_false(condition: bool, message: str = "Expected condition to be False"):
    if condition:
        raise AssertionError(f"Assertion failed: {message}")


def assert_equal(actual, expected, message: str = ""):
    if actual != expected:
        msg = f"Expected {expected!r}, got {actual!r}"
        if message:
            msg = f"{message} ({msg})"
        raise AssertionError(f"Assertion failed: {msg}")


def assert_not_equal(actual, expected, message: str = ""):
    if actual == expected:
        msg = f"Expected value not to equal {expected!r}"
        if message:
            msg = f"{message} ({msg})"
        raise AssertionError(f"Assertion failed: {msg}")


def assert_almost_equal(actual: float, expected: float, delta: float = 1e-4, message: str = ""):
    if math.isnan(actual) or math.isnan(expected):
        raise AssertionError(f"NaN comparison in assert_almost_equal: actual={actual}, expected={expected}")
    diff = abs(actual - expected)
    if diff > delta:
        msg = f"Expected {expected} +/- {delta}, got {actual} (diff={diff})"
        if message:
            msg = f"{message} ({msg})"
        raise AssertionError(f"Assertion failed: {msg}")


def assert_in_range(val: float, min_val: float, max_val: float, message: str = ""):
    if not (min_val <= val <= max_val):
        msg = f"Value {val} outside bounds [{min_val}, {max_val}]"
        if message:
            msg = f"{message} ({msg})"
        raise AssertionError(f"Assertion failed: {msg}")


def assert_zero_emojis(text: str, context: str = ""):
    if not isinstance(text, str):
        return
    matches = EMOJI_PATTERN.findall(text)
    if matches:
        raise AssertionError(
            f"Zero-emoji mandate violated in {context}: found emojis {matches!r}"
        )


def assert_raises(exc_type, func, *args, **kwargs):
    try:
        func(*args, **kwargs)
    except exc_type as e:
        return e
    except Exception as e:
        raise AssertionError(f"Expected exception {exc_type.__name__}, but {type(e).__name__} was raised: {e}")
    raise AssertionError(f"Expected exception {exc_type.__name__}, but no exception was raised")
