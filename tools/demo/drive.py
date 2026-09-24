#!/usr/bin/env python3
"""Drives the LectureLoop demo on an Android emulator and records one clip per scene.

Everything on screen is the real app: real Gemini calls, real RevenueCat Test Store purchase.
The only staged thing is the clock jump for "next day", which the video labels as simulated.

Usage:  python3 tools/demo/drive.py --out demo/raw [--scenes hook,record,...]
Needs:  adb on PATH or ANDROID_HOME, the debug APK installed, the three demo recordings in
        /sdcard/Download (record_demo.sh does all of this). Standard library only.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "io.github.ryugi62.lectureloop"
ADB = os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb") if os.environ.get("ANDROID_HOME") else "adb"


def adb(*args, check=True, capture=True):
    r = subprocess.run([ADB, *args], capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def shell(cmd, check=True):
    return adb("shell", cmd, check=check)


# ---------- UI tree ----------

class Node:
    def __init__(self, el):
        self.id = el.get("resource-id", "")
        self.text = el.get("text", "")
        self.desc = el.get("content-desc", "")
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", el.get("bounds", "[0,0][0,0]")))
        self.box = (x1, y1, x2, y2)

    @property
    def center(self):
        x1, y1, x2, y2 = self.box
        return (x1 + x2) // 2, (y1 + y2) // 2


def nodes():
    for _ in range(5):
        out = shell("uiautomator dump /sdcard/ll-ui.xml", check=False)
        if "dumped" in out:
            xml = adb("exec-out", "cat", "/sdcard/ll-ui.xml")
            try:
                return [Node(e) for e in ET.fromstring(xml).iter("node")]
            except ET.ParseError:
                pass
        time.sleep(0.6)
    return []


def find(id=None, text=None, regex=None):
    for n in nodes():
        if id and n.id == id:
            return n
        if text and n.text == text:
            return n
        if regex and (re.search(regex, n.text) or re.search(regex, n.desc)):
            return n
    return None


def wait(timeout=30, **query):
    end = time.time() + timeout
    while time.time() < end:
        n = find(**query)
        if n:
            return n
        time.sleep(0.5)
    raise TimeoutError(f"not on screen after {timeout}s: {query}")


_fixed = {}


def tap_fixed(id, pause=0.8):
    """Tap an element that never moves (the bottom CTA) without a fresh UI dump."""
    if id not in _fixed:
        _fixed[id] = wait(id=id).center
    x, y = _fixed[id]
    shell(f"input tap {x} {y}")
    time.sleep(pause)


def tap(node_or_query=None, pause=0.8, **query):
    n = node_or_query if isinstance(node_or_query, Node) else wait(**query)
    x, y = n.center
    shell(f"input tap {x} {y}")
    time.sleep(pause)
    return n


def swipe_up(fraction=0.45, ms=600):
    w, h = screen_size()
    shell(f"input swipe {w // 2} {int(h * 0.75)} {w // 2} {int(h * (0.75 - fraction))} {ms}")
    time.sleep(0.8)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", shell("wm size"))
    return int(m.group(1)), int(m.group(2))


def type_text(s):
    shell("input text " + s.replace(" ", "%s"))
    time.sleep(0.5)


# ---------- recording ----------

class Clip:
    """adb screenrecord for one scene (the device limit is 180 s; scenes are far shorter)."""

    def __init__(self, name, out_dir):
        self.name, self.out_dir = name, out_dir
        self.remote = f"/sdcard/ll-{name}.mp4"

    def __enter__(self):
        shell(f"rm -f {self.remote}", check=False)
        self.proc = subprocess.Popen([ADB, "shell", f"screenrecord --bit-rate 8000000 --time-limit 170 {self.remote}"])
        time.sleep(1.2)
        self.t0 = time.time()
        return self

    def __exit__(self, *exc):
        time.sleep(1.0)
        shell("pkill -INT screenrecord", check=False)
        self.proc.wait(timeout=20)
        time.sleep(1.0)
        os.makedirs(self.out_dir, exist_ok=True)
        adb("pull", self.remote, os.path.join(self.out_dir, f"{self.name}.mp4"))
        self.seconds = round(time.time() - self.t0, 1)
        return False


# ---------- app data ----------

def lectures():
    names = shell(f"run-as {PKG} ls files/lectures", check=False).split()
    out = []
    for n in names:
        if n.endswith(".json"):
            out.append(json.loads(shell(f"run-as {PKG} cat files/lectures/{n}")))
    return out


def correct_answers(course):
    lec = next(l for l in lectures() if l["course"] == course)
    return [q["answerIndex"] for q in lec["card"]["quiz"]]


# ---------- scenes ----------

def import_file(file_text, course):
    tap(id="import")
    tap(text=file_text, timeout=20)  # the file name itself — the thumbnail's desc opens a preview instead
    tap(id="course")
    shell("input keyevent 123 " + " ".join(["67"] * 45))  # clear the remembered class name
    type_text(course)
    shell("input keyevent 111")  # hide keyboard
    time.sleep(0.6)
    tap(id="cta")


def scene_hook():
    shell(f"am start -n {PKG}/.MainActivity")
    wait(id="cta", timeout=20)
    time.sleep(4)


def scene_record():
    tap(id="cta", pause=1.5)          # Record a lecture
    tap(id="cta", pause=6)            # Start recording (timer runs)
    tap(id="icon-Close", pause=1)     # cancel: this scene only shows the recorder


def scene_import():
    import_file("DSP lecture 5.m4a", "Signals and Systems")
    time.sleep(1.5)


def scene_processing():
    wait(regex=r"things? your lecturer said", timeout=120)
    time.sleep(1.5)


def scene_card():
    tap(regex=r"^What they said$", pause=2)                     # unfold the lecturer's words
    tap(regex=r"^00:3\d$|^00:4\d$", pause=2)                     # play from the exam hint
    swipe_up(0.5)
    swipe_up(0.5)
    time.sleep(1.5)
    swipe_up(0.6)
    time.sleep(2)
    shell("input keyevent 4")                                     # back home
    time.sleep(1)


def seed_second_lecture():
    """Off camera: the second free lecture of the week, so the third one meets the paywall."""
    import_file("Data Structures - linked lists.m4a", "Data Structures")
    wait(regex=r"things? your lecturer said", timeout=120)
    shell("input keyevent 4")
    time.sleep(1)


def jump_one_day():
    adb("root", check=False)
    time.sleep(2)
    shell("settings put global auto_time 0", check=False)
    now = int(shell("date +%s").strip())
    t = time.gmtime(now + 86400 + 120)
    shell(time.strftime("date -u %m%d%H%M%Y.%S", t), check=False)
    shell(f"am force-stop {PKG}")
    shell(f"am start -n {PKG}/.MainActivity")
    wait(id="cta", timeout=20)


def scene_nextday(wrong=1):
    answers = correct_answers("Signals and Systems")
    time.sleep(2.5)
    tap(id="due-card", pause=1.5)
    for i, a in enumerate(answers):
        pick = (a + 1) % 4 if i == wrong else a
        tap(id=f"choice-{pick}", pause=1.4)
        tap_fixed("cta", pause=0.4)
    time.sleep(3)
    tap(id="cta", pause=1.5)   # Done


def scene_paywall():
    import_file("Electromagnetics - Coulomb.m4a", "Electromagnetics")   # third lecture this week → paywall
    wait(id="plan-semester", timeout=30)
    time.sleep(3)
    tap(id="cta", pause=2)                         # Start Semester Pass → Test Store dialog
    tap(regex=r"(?i)^(test valid purchase|purchase|buy|confirm)", timeout=30, pause=2)
    wait(regex=r"things? your lecturer said", timeout=120)   # the waiting recording is built right away
    time.sleep(3)
    shell("input keyevent 4")
    time.sleep(1.5)


def scene_long():
    """A 74-minute public-domain lecture recording (LibriVox) — shows the windowed analysis on a real length."""
    import_file("Philosophy 74 min.m4a", "Philosophy")
    wait(regex=r"things? your lecturer said|No exam hints", timeout=240)
    time.sleep(1.5)
    swipe_up(0.5)
    time.sleep(1.5)
    shell("input keyevent 4")
    time.sleep(1)


def scene_account():
    tap(id="icon-Account", pause=2)
    tap(id="cta", pause=4)                         # Manage subscription → Customer Center
    time.sleep(2)


SCENES = {
    "hook": scene_hook,
    "record": scene_record,
    "import": scene_import,
    "processing": scene_processing,
    "card": scene_card,
    "nextday": scene_nextday,
    "paywall": scene_paywall,
    "long": scene_long,
    "account": scene_account,
}
OFF_CAMERA = {"nextday": [seed_second_lecture, jump_one_day]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="demo/raw")
    ap.add_argument("--scenes", default=",".join(SCENES))
    args = ap.parse_args()
    timeline = []
    for name in args.scenes.split(","):
        for prep in OFF_CAMERA.get(name, []):
            print(f"[prep] {prep.__name__}", flush=True)
            prep()
        print(f"[scene] {name}", flush=True)
        with Clip(name, args.out) as clip:
            SCENES[name]()
        timeline.append({"scene": name, "seconds": clip.seconds})
        print(f"[done] {name} {clip.seconds}s", flush=True)
    with open(os.path.join(args.out, "timeline.json"), "w") as f:
        json.dump(timeline, f, indent=1)
    print(json.dumps(timeline))


if __name__ == "__main__":
    sys.exit(main())
