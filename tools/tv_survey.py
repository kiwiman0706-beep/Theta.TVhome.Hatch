#!/usr/bin/env python3
"""Android TV の「構造」を ADB で読むだけで調べ、docs/device/<型番>/ に残す。

テレビの設定やアプリは一切変えない（読むだけのコマンドしか使わない）。
使い方（PC、テレビと同じ LAN、adb connect 済み）：

    python tools/tv_survey.py --serial 192.168.1.50:5555
    python tools/tv_survey.py --serial 192.168.1.50:5555 --label 職場

出力：
    docs/device/<型番>/survey-<日付>.json   機械が読む要約
    docs/device/<型番>/raw-<日付>/*.txt     コマンドの出力そのまま
型番（ro.product.model）はテレビから読む。人が読む表は docs/device/<型番>.md（_TEMPLATE.md から作る）。
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
PKG = "app.thetahatch.tvhome"

# 読むだけのコマンド（名前, adb shell のコマンド）
COMMANDS: list[tuple[str, str]] = [
    ("getprop", "getprop"),
    ("wm-size", "wm size"),
    ("wm-density", "wm density"),
    ("meminfo", "cat /proc/meminfo"),
    ("cpuinfo", "cat /proc/cpuinfo"),
    ("df", "df -h /data /storage/emulated 2>/dev/null"),
    ("storage", "ls -la /storage 2>/dev/null"),
    ("packages-system", "pm list packages -s"),
    ("packages-user", "pm list packages -3"),
    ("packages-disabled", "pm list packages -d"),
    ("home", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("home-all", "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("leanback-launchers", "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER"),
    ("all-apps-intent", "cmd package query-activities --brief -a android.intent.action.ALL_APPS"),
    ("tv-view-channel", "cmd package query-activities --brief -a android.intent.action.VIEW -d content://android.media.tv/channel"),
    ("tv-view-passthrough", "cmd package query-activities --brief -a android.intent.action.VIEW -d content://android.media.tv/passthrough/x"),
    ("recording-schedules", "cmd package query-activities --brief -a android.media.tv.action.VIEW_RECORDING_SCHEDULES"),
    ("home-settings", "cmd package query-activities --brief -a android.settings.HOME_SETTINGS"),
    ("unknown-sources-settings", "cmd package query-activities --brief -a android.settings.MANAGE_UNKNOWN_APP_SOURCES"),
    ("overlay-settings", "cmd package query-activities --brief -a android.settings.action.MANAGE_OVERLAY_PERMISSION"),
    ("accessibility-settings", "cmd package query-activities --brief -a android.settings.ACCESSIBILITY_SETTINGS"),
    ("tv-input", "dumpsys tv_input"),
    ("input-devices", "dumpsys input"),
    ("hdmi-cec", "dumpsys hdmi_control"),
    ("roles", "cmd role get-role-holders android.app.role.HOME 2>/dev/null"),
    ("settings-secure", "settings list secure"),
    ("settings-global", "settings list global"),
    ("connectivity", "dumpsys connectivity --short 2>/dev/null || dumpsys connectivity"),
    ("accessibility", "dumpsys accessibility"),
    ("media-sessions", "dumpsys media_session"),
    ("activities-top", "dumpsys activity activities"),
]

# TVhome が入っていれば、その窓口も読む
TVHOME = [
    ("tvhome-status", f"dumpsys activity service {PKG}/app.thetahatch.adb.StatusService"),
    ("tvhome-probe", f"dumpsys activity service {PKG}/app.thetahatch.adb.StatusService probe"),
]

PROPS = [
    "ro.product.model", "ro.product.manufacturer", "ro.product.brand", "ro.product.device", "ro.product.name",
    "ro.build.version.release", "ro.build.version.sdk", "ro.build.version.security_patch", "ro.build.fingerprint",
    "ro.board.platform", "ro.hardware", "ro.product.cpu.abi", "ro.build.characteristics",
    "ro.config.low_ram", "ro.sf.lcd_density",
]


def adb(serial: str, *args: str, timeout: int = 60) -> str:
    cmd = ["adb", "-s", serial, *args]
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    except FileNotFoundError:
        sys.exit("adb が見つかりません（Windows：winget install Google.PlatformTools、Mac：brew install android-platform-tools）")
    except subprocess.TimeoutExpired:
        return "(時間切れ)"
    return (r.stdout or "") + (("\n[stderr]\n" + r.stderr) if r.stderr.strip() else "")


def parse_props(text: str) -> dict[str, str]:
    out = {}
    for m in re.finditer(r"^\[([^\]]+)\]: \[(.*)\]$", text, re.M):
        out[m.group(1)] = m.group(2)
    return out


def lines(text: str) -> list[str]:
    return [l.strip() for l in text.splitlines() if l.strip() and not l.startswith("[stderr]")]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True, help="adb devices に出る名前（例 192.168.1.50:5555）")
    ap.add_argument("--label", default="", help="置き場所などのメモ（例 自宅・職場）")
    ap.add_argument("--out", default="", help="出力先（既定 docs/device/<型番>）")
    a = ap.parse_args()

    state = adb(a.serial, "get-state").strip()
    if state != "device":
        sys.exit(f"{a.serial} につながっていません（{state}）。adb connect {a.serial} とテレビの許可を確かめてください")

    raw_props = adb(a.serial, "shell", "getprop")
    props = parse_props(raw_props)
    model = re.sub(r"[^A-Za-z0-9._-]", "_", props.get("ro.product.model", "unknown")) or "unknown"
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M")
    out = pathlib.Path(a.out) if a.out else ROOT / "docs" / "device" / model
    raw = out / f"raw-{stamp}"
    raw.mkdir(parents=True, exist_ok=True)

    has_tvhome = PKG in adb(a.serial, "shell", "pm", "list", "packages", PKG)
    results: dict[str, str] = {}
    for name, cmd in COMMANDS + (TVHOME if has_tvhome else []):
        print(f"… {name}", flush=True)
        text = raw_props if name == "getprop" else adb(a.serial, "shell", cmd)
        results[name] = text
        (raw / f"{name}.txt").write_text(text, encoding="utf-8")

    mem = re.search(r"MemTotal:\s+(\d+)", results.get("meminfo", ""))
    summary = {
        "surveyed": dt.datetime.now().isoformat(timespec="seconds"),
        "label": a.label,
        "serial": a.serial,
        "props": {k: props.get(k) for k in PROPS},
        "screen": lines(results.get("wm-size", "")) + lines(results.get("wm-density", "")),
        "memTotalMB": int(mem.group(1)) // 1024 if mem else None,
        "home": lines(results.get("home", ""))[-1:] or None,
        "homeCandidates": lines(results.get("home-all", "")),
        "leanbackLaunchers": len(lines(results.get("leanback-launchers", ""))),
        "intents": {k: lines(results.get(k, "")) for k in (
            "all-apps-intent", "tv-view-channel", "tv-view-passthrough", "recording-schedules",
            "home-settings", "unknown-sources-settings", "overlay-settings", "accessibility-settings")},
        "tvishPackages": sorted(
            p.removeprefix("package:") for p in lines(results.get("packages-system", "")) + lines(results.get("packages-user", ""))
            if re.search(r"tv|sharp|aquos|record|tuner|dtv|launcher", p, re.I)
        ),
        "userPackages": [p.removeprefix("package:") for p in lines(results.get("packages-user", ""))],
        "disabledPackages": [p.removeprefix("package:") for p in lines(results.get("packages-disabled", ""))],
        "tvhome": {"installed": has_tvhome},
    }
    if has_tvhome:
        for k in ("tvhome-status", "tvhome-probe"):
            body = results.get(k, "").strip()
            try:
                summary["tvhome"][k.removeprefix("tvhome-")] = json.loads(body.splitlines()[-1])
            except Exception:
                summary["tvhome"][k.removeprefix("tvhome-")] = body[:2000]

    path = out / f"survey-{stamp}.json"
    path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n型番：{model}\n要約：{path}\nそのままの出力：{raw}")
    md = out.parent / f"{model}.md"
    if not md.exists():
        print(f"人が読む表がまだありません：docs/device/_TEMPLATE.md を {md.name} として写して埋めてください")


if __name__ == "__main__":
    main()
