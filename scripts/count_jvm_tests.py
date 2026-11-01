import os
import glob
import xml.etree.ElementTree as ET

total_tests = 0
total_fails = 0
total_errors = 0
total_skipped = 0
suites = 0
modules = {}

for f in glob.glob('**/test-results/**/*.xml', recursive=True):
    # Skip e2e test reports
    if 'tests' in f.split(os.sep):
        continue
    try:
        tree = ET.parse(f)
        root = tree.getroot()
        t = int(root.attrib.get('tests', 0))
        fl = int(root.attrib.get('failures', 0))
        er = int(root.attrib.get('errors', 0))
        sk = int(root.attrib.get('skipped', 0))
        # Get module path (e.g. core/data or feature/editor)
        parts = f.replace('\\', '/').split('/build/test-results/')[0]
        mod = parts
        if mod not in modules:
            modules[mod] = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': 0}
        modules[mod]['tests'] += t
        modules[mod]['failures'] += fl
        modules[mod]['errors'] += er
        modules[mod]['skipped'] += sk
        modules[mod]['suites'] += 1
        total_tests += t
        total_fails += fl
        total_errors += er
        total_skipped += sk
        suites += 1
    except Exception as e:
        print(f"Error parsing {f}: {e}")

print("=" * 80)
print("JVM UNIT TEST EXECUTION SUMMARY")
print("=" * 80)
print(f"Total Suites: {suites}")
print(f"Total Tests:  {total_tests}")
print(f"Failures:     {total_fails}")
print(f"Errors:       {total_errors}")
print(f"Skipped:      {total_skipped}")
print("-" * 80)
print("Per-Module Breakdown:")
for m, c in sorted(modules.items()):
    print(f"  {m:<25} | Suites: {c['suites']:<3} | Tests: {c['tests']:<4} | Failures: {c['failures']:<2} | Errors: {c['errors']:<2}")
print("=" * 80)

