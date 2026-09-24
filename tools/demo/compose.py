#!/usr/bin/env python3
"""Builds the submission video from the per-scene clips recorded by drive.py.

Layout 1920x1080: the phone screen (real recording) on the left, the caption on the right.
Clips longer than their slot are sped up and labelled with the factor; shorter ones hold the last frame.
Writes LectureLoop-demo.mp4 and LectureLoop-demo.en.srt, and fails if the video is 2 minutes or longer.

Usage: FFMPEG=/path/to/ffmpeg python3 tools/demo/compose.py --raw demo/raw --out demo/out
"""
import argparse
import json
import os
import re
import subprocess
import sys
import textwrap

HERE = os.path.dirname(os.path.abspath(__file__))
FF = os.environ.get("FFMPEG", "ffmpeg")
FONT = os.environ.get("CAPTION_FONT", "/System/Library/Fonts/Supplemental/Arial.ttf")
FONT_BOLD = os.environ.get("CAPTION_FONT_BOLD", "/System/Library/Fonts/Supplemental/Arial Bold.ttf")
BG, TEXT, SUB, BLUE = "0xF2F4F6", "0x191F28", "0x4E5968", "0x3182F6"


def run(args):
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr[-3000:])
        raise SystemExit(f"ffmpeg failed: {' '.join(args[:6])}...")
    return r


def esc(path):
    return path.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")


def write(path, text):
    with open(path, "w") as f:
        f.write(text)
    return path


def caption_filters(work, scene, x, width_chars, speed_label, emulator_label=True):
    head = write(os.path.join(work, f"{scene['scene']}.head.txt"), scene["head"])
    body = write(os.path.join(work, f"{scene['scene']}.body.txt"), "\n".join(textwrap.wrap(scene["body"], width_chars)))
    f = [
        f"drawtext=fontfile='{esc(FONT_BOLD)}':textfile='{esc(head)}':x={x}:y=300:fontsize=64:fontcolor={TEXT}",
        f"drawtext=fontfile='{esc(FONT)}':textfile='{esc(body)}':x={x}:y=410:fontsize=40:fontcolor={SUB}:line_spacing=18",
    ]
    if emulator_label:
        f.append(f"drawtext=fontfile='{esc(FONT)}':text='Real app on an Android emulator':x={x}:y=980:fontsize=26:fontcolor={BLUE}")
    if speed_label:
        lab = write(os.path.join(work, f"{scene['scene']}.speed.txt"), speed_label)
        f.append(f"drawtext=fontfile='{esc(FONT)}':textfile='{esc(lab)}':x={x}:y=1020:fontsize=26:fontcolor={SUB}")
    return ",".join(f)


def render_scene(raw, work, scene, recorded):
    out = os.path.join(work, f"{scene['scene']}.mp4")
    target = scene["seconds"]
    clip = os.path.join(raw, f"{scene['scene']}.mp4")
    if scene["scene"] == "end" or not os.path.exists(clip):
        vf = caption_filters(work, scene, 360, 52, None, emulator_label=False)
        run([FF, "-y", "-f", "lavfi", "-i", f"color=c={BG}:s=1920x1080:r=30:d={target}", "-vf", vf,
             "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", "30", out])
        return out, target
    length = recorded.get(scene["scene"], target)
    # "trim" keeps real time (used where timing is the point, e.g. processing); "speed" fits the slot.
    factor = 1.0 if scene.get("fit") == "trim" else (min(1.0, target / length) if length > 0 else 1.0)
    label = f"Sped up {1 / factor:.1f}x" if factor < 0.83 else None
    fc = (
        f"[0:v]fps=30,setpts=PTS*{factor:.4f},scale=-2:1000,tpad=stop_mode=clone:stop_duration={target}[ph];"
        f"[1:v]drawbox=x=196:y=36:w=470:h=1008:color=0xD1D6DB:t=4[bg];"
        f"[bg][ph]overlay=x=200:y=40:shortest=0,{caption_filters(work, scene, 780, 40, label)}[v]"
    )
    run([FF, "-y", "-i", clip, "-f", "lavfi", "-i", f"color=c={BG}:s=1920x1080:r=30:d={target}",
         "-filter_complex", fc, "-map", "[v]", "-t", str(target), "-c:v", "libx264", "-pix_fmt", "yuv420p", "-r", "30", out])
    return out, target


def srt_time(t):
    h, rem = divmod(int(t * 1000), 3_600_000)
    m, rem = divmod(rem, 60_000)
    s, ms = divmod(rem, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", default="demo/raw")
    ap.add_argument("--out", default="demo/out")
    args = ap.parse_args()
    board = json.load(open(os.path.join(HERE, "storyboard.json")))
    total = sum(s["seconds"] for s in board["scenes"])
    if total >= board["limit_seconds"] + 5 or total >= 120:
        raise SystemExit(f"storyboard is {total}s — must stay under 2:00")
    recorded = {}
    tl = os.path.join(args.raw, "timeline.json")
    if os.path.exists(tl):
        recorded = {x["scene"]: x["seconds"] for x in json.load(open(tl))}
    work = os.path.join(args.out, "work")
    os.makedirs(work, exist_ok=True)
    parts, t, srt = [], 0.0, []
    for i, scene in enumerate(board["scenes"], 1):
        path, secs = render_scene(args.raw, work, scene, recorded)
        parts.append(path)
        srt.append(f"{i}\n{srt_time(t)} --> {srt_time(t + secs)}\n{scene['head']}. {scene['body']}\n")
        t += secs
    listing = write(os.path.join(work, "concat.txt"), "".join(f"file '{os.path.abspath(p)}'\n" for p in parts))
    final = os.path.join(args.out, "LectureLoop-demo.mp4")
    run([FF, "-y", "-f", "concat", "-safe", "0", "-i", listing, "-c", "copy", final])
    write(os.path.join(args.out, "LectureLoop-demo.en.srt"), "\n".join(srt))
    probe = subprocess.run([FF, "-i", final], capture_output=True, text=True).stderr
    m = re.search(r"Duration: (\d+):(\d+):([\d.]+)", probe)
    seconds = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))
    print(f"{final} {seconds:.1f}s")
    if seconds >= 120:
        raise SystemExit("video is 2:00 or longer")


if __name__ == "__main__":
    main()
