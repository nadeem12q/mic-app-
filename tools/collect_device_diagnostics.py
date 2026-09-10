#!/usr/bin/env python3
"""Read-only Android routing snapshots; run beside a locally connected phone."""
import argparse
import datetime
from pathlib import Path
import re
import shutil
import subprocess
import sys


def run(args):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=30)
        return result.returncode, result.stdout + result.stderr
    except subprocess.TimeoutExpired:
        return 124, "Command timed out after 30 seconds\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB device serial when more than one device is attached")
    parser.add_argument("--label", default="baseline", help="Scenario name, e.g. whatsapp-call")
    parser.add_argument("--output", type=Path, default=Path("artifacts/device-diagnostics"))
    args = parser.parse_args()
    adb = shutil.which("adb")
    if not adb:
        sys.exit("ADB not found. Install Android platform-tools and add them to PATH.")
    code, listing = run([adb, "devices"])
    if code:
        sys.exit(listing)
    ready = [line.split()[0] for line in listing.splitlines() if re.match(r"^\S+\s+device$", line)]
    if args.serial:
        if args.serial not in ready:
            sys.exit("Selected phone is not connected/authorized. Check adb devices on this computer.")
        serial = args.serial
    elif len(ready) == 1:
        serial = ready[0]
    else:
        sys.exit("Connect and authorize one phone, or specify --serial.\n" + listing)
    label = re.sub(r"[^A-Za-z0-9_-]", "_", args.label)[:60] or "baseline"
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    destination = args.output / f"{stamp}-{label}"
    destination.mkdir(parents=True, exist_ok=False)
    commands = {
        "shell-identity": ["id"],
        "phone-model": ["getprop", "ro.product.model"],
        "android-version": ["getprop", "ro.build.version.release"],
        "build": ["getprop", "ro.build.display.id"],
        "audio": ["dumpsys", "audio"],
        "audio-policy": ["dumpsys", "media.audio_policy"],
        "shell-permissions": ["dumpsys", "package", "com.android.shell"],
        "test-app-permissions": ["dumpsys", "package", "com.microute.test"],
    }
    for name, command in commands.items():
        code, output = run([adb, "-s", serial, "shell", *command])
        (destination / f"{name}.txt").write_text(f"Exit code: {code}\n{output}", encoding="utf-8")
        print(f"{name}: {'captured' if code == 0 else f'failed ({code}); inspect report'}")
    print(f"Saved to {destination}. No routing or permission changes requested.")
    print("A successful dump is not proof of microphone control. Inspect identifiers before sharing.")


if __name__ == "__main__":
    main()
