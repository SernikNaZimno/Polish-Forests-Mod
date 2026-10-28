#!/usr/bin/env python3
"""Analysis of a -PviewCheckCalibrate run (step T8): the stability window W of the adaptive reference frame of -PviewCheck.

Reads build/view-check/calibration.tsv (one row per probe frame, written by gametest ViewReady) and prints, per frame, the
moments of change of the image, the longest gap between two consecutive changes (the gap from the screenshot to the first
change counts), the last change, and then for a range of windows W the loss of the adaptive reference against the frame
after the whole safety limit:

  - the reference of a window W is the first probe at which the image has not changed for W seconds (the "change" column:
    the probe differs from the anchor frame by more than the FrameDiff tolerance) and the view is idle (flags queue, cone,
    server, light; "queue" empty queue of the client, "cone" every section met by the view rays is meshed, "server" no
    generation work around the camera, "light" client light work);
  - loss: the reference differs from the final frame (finalEqual = 0), i.e. the adaptive check could pass a frame that the
    long wait would fail (or the reverse).

Recommended W = max(5, 2 x the longest gap between two consecutive changes in the data), rounded up to a whole second.

Usage: python tools/dev/view_calibration.py [calibration.tsv] [--windows 3,5,8,10,15,20,30] [--idle strict|cone|none] [-v]
  --idle strict (default): the view must be idle in the sense of the task (queue empty, cone ready, server and light idle);
  --idle cone: the compile queue of far sections may still run (cone ready, server and light idle);
  --idle none: the image alone decides.
"""
import argparse
import math
import os
import sys
from collections import OrderedDict


def load(path):
    frames = OrderedDict()
    with open(path, encoding="utf-8") as f:
        header = f.readline().rstrip("\n").split("\t")
        for line in f:
            cells = line.rstrip("\n").split("\t")
            if len(cells) != len(header) or cells[0] == header[0]:
                continue
            row = dict(zip(header, cells))
            for k in header[1:]:
                row[k] = float(row[k])
            frames.setdefault(row["frame"], []).append(row)
    return frames


def idle_ok(row, mode):
    if mode == "none":
        return True
    ok = row["cone"] == 1 and row["server"] == 1 and row["light"] == 0
    return ok and (mode == "cone" or row["queue"] == 1)


def changes_of(rows):
    return [r["t"] for r in rows[1:] if r["change"] == 1]


def gaps_of(rows):
    start = rows[0]["t"]
    times = [start] + changes_of(rows)
    return [b - a for a, b in zip(times, times[1:])]


def reference(rows, window, mode):
    """The first probe that has been unchanged for `window` seconds with an idle view, or None."""
    anchor = rows[0]["t"]
    for r in rows[1:]:
        if r["change"] == 1:
            anchor = r["t"]
            continue
        if r["t"] - anchor >= window - 1e-6 and idle_ok(r, mode):
            return r
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file", nargs="?", default=os.path.join("build", "view-check", "calibration.tsv"))
    ap.add_argument("--windows", default="3,5,8,10,15,20,30")
    ap.add_argument("--idle", default="strict", choices=["strict", "cone", "none"])
    ap.add_argument("-v", action="store_true", help="print the changes of every frame")
    a = ap.parse_args()
    frames = load(a.file)
    if not frames:
        print("no rows in", a.file)
        return 1
    longest = 0.0
    print("%-44s %6s %7s %8s %9s %10s %10s" % ("frame", "probes", "changes", "maxgap_s", "lastchg_s", "queue_s", "idle_s"))
    for name, rows in frames.items():
        ch = changes_of(rows)
        gp = gaps_of(rows)
        mg = max(gp) if gp else 0.0
        longest = max(longest, mg)
        t0 = rows[0]["t"]
        last = (ch[-1] - t0) if ch else 0.0
        q = next((r["t"] - t0 for r in rows[1:] if r["queue"] == 1), float("nan"))
        i = next((r["t"] - t0 for r in rows[1:] if idle_ok(r, "strict")), float("nan"))
        print("%-44s %6d %7d %8.1f %9.1f %10.1f %10.1f" % (name, len(rows) - 1, len(ch), mg, last, q, i))
        if a.v:
            print("    changes at (s after the screenshot):", ", ".join("%.0f" % (c - t0) for c in ch) or "-")
    rec = max(5, math.ceil(2 * longest))
    print()
    print("frames: %d; longest gap between two consecutive changes: %.1f s -> recommended W = max(5, 2 x gap) = %d s" % (len(frames), longest, rec))
    print()
    print("loss of the adaptive reference against the final frame (idle mode: %s)" % a.idle)
    print("%6s %8s %10s %10s %6s" % ("W_s", "no_ref", "mean_s", "max_s", "loss"))
    wins = sorted(set([float(x) for x in a.windows.split(",")] + [float(rec)]))
    for w in wins:
        lost = []
        none = []
        times = []
        for name, rows in frames.items():
            r = reference(rows, w, a.idle)
            if r is None:
                none.append(name)
                continue
            times.append(r["t"] - rows[0]["t"])
            if r["finalEqual"] != 1:
                lost.append(name)
        mean = sum(times) / len(times) if times else float("nan")
        print("%6.0f %8d %10.1f %10.1f %6d%s" % (w, len(none), mean, max(times) if times else float("nan"), len(lost),
                                                 ("   <- " + ", ".join(lost)) if lost else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
