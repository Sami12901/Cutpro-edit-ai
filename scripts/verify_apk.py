import os
import zipfile
import time

apk_path = os.path.abspath("app/build/outputs/apk/debug/app-debug.apk")
if not os.path.exists(apk_path):
    print(f"ERROR: APK not found at {apk_path}")
    exit(1)

size = os.path.getsize(apk_path)
mtime = time.ctime(os.path.getmtime(apk_path))

print("=" * 80)
print("DEBUG APK VERIFICATION REPORT")
print("=" * 80)
print(f"APK Absolute Path: {apk_path}")
print(f"File Size:        {size:,} bytes ({size / (1024 * 1024):.2f} MB)")
print(f"Timestamp:        {mtime}")

try:
    with zipfile.ZipFile(apk_path, "r") as z:
        entries = z.namelist()
        dex_files = [f for f in entries if f.endswith(".dex")]
        print(f"ZIP Validation:   VALID ({len(entries)} archive entries)")
        print(f"AndroidManifest:  {'AndroidManifest.xml' in entries}")
        print(f"resources.arsc:   {'resources.arsc' in entries}")
        print(f"DEX Files:        {len(dex_files)} ({', '.join(dex_files)})")
        test_crc = z.testzip()
        if test_crc is None:
            print("CRC Check:        ALL ENTRIES OK (NO CRC CORRUPTION)")
        else:
            print(f"CRC Check FAILED: Corrupted file: {test_crc}")
            exit(1)
except Exception as e:
    print(f"ZIP Validation FAILED: {e}")
    exit(1)

print("=" * 80)
print("VERDICT: DEBUG APK IS VALID AND READY")
print("=" * 80)

