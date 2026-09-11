# Copyright 2026 Timofey Krestyanov
# SPDX-License-Identifier: Apache-2.0
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
FIXED_BUDGET_MS = 1000.0 / 60.0

data = json.load(open(os.path.join(HERE, "frame-budget.json")))
frames = data["frames"]

W, H = 1200, 680
L, R, T = 138, 36, 108
PH = 380
PT = T + PH
STRIP_TOP = PT + 34
CELL = 12
PW = W - L - R
YMAX = 36.0
STEP = PW / len(frames)

BG, PANEL = "#111318", "#171a21"
GRID, AXIS = "#262a33", "#7b8494"
WITHIN, LOST = "#4a5464", "#d98c3f"
BUDGET, FIXED = "#8b7cf6", "#6a7280"
ON_TIME_CELL, LATE_CELL = "#3f7f57", "#d98c3f"
TITLE, SUB, INK = "#e8eaee", "#99a1af", "#e8eaee"


def x_left(i):
    return L + i * STEP


def x_mid(i):
    return L + STEP * (i + 0.5)


def y(ms):
    return T + PH * (1 - min(ms, YMAX) / YMAX)


def late_by_own(f):
    return f["totalMs"] > f["deadlineMs"]


def late_by_fixed(f):
    return f["totalMs"] > FIXED_BUDGET_MS


svg = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
    f'font-family="ui-sans-serif,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif">',
    f'<rect width="{W}" height="{H}" fill="{BG}"/>',
    f'<rect x="{L}" y="{T}" width="{PW}" height="{PH}" fill="{PANEL}"/>',
]

budget_region = [f"{x_left(0):.1f},{PT}"]
for i, f in enumerate(frames):
    budget_region.append(f"{x_left(i):.1f},{y(f['deadlineMs']):.1f}")
    budget_region.append(f"{x_left(i + 1):.1f},{y(f['deadlineMs']):.1f}")
budget_region.append(f"{x_left(len(frames)):.1f},{PT}")
svg.append(f'<polygon points="{" ".join(budget_region)}" fill="{BUDGET}" opacity="0.14"/>')

for ms, label in ((8.33, "8.3"), (16.67, "16.7"), (24.98, "25.0"), (33.3, "33.3")):
    svg.append(f'<line x1="{L}" y1="{y(ms):.1f}" x2="{L + PW}" y2="{y(ms):.1f}" stroke="{GRID}"/>')
    svg.append(f'<text x="{L - 12}" y="{y(ms) + 4:.1f}" fill="{AXIS}" font-size="13" text-anchor="end">{label}</text>')
svg.append(f'<text x="{L - 12}" y="{T - 14}" fill="{AXIS}" font-size="12" text-anchor="end">ms</text>')
svg.append(f'<line x1="{L}" y1="{PT}" x2="{L + PW}" y2="{PT}" stroke="{GRID}"/>')

bar_w = STEP * 0.66
for i, f in enumerate(frames):
    total, deadline = f["totalMs"], f["deadlineMs"]
    bx = x_mid(i) - bar_w / 2
    within_top = y(min(total, deadline))
    svg.append(f'<rect x="{bx:.1f}" y="{within_top:.1f}" width="{bar_w:.1f}" height="{PT - within_top:.1f}" fill="{WITHIN}" rx="1"/>')
    if late_by_own(f):
        svg.append(f'<rect x="{bx:.1f}" y="{y(total):.1f}" width="{bar_w:.1f}" height="{within_top - y(total):.1f}" fill="{LOST}" rx="1"/>')

svg.append(f'<line x1="{L}" y1="{y(FIXED_BUDGET_MS):.1f}" x2="{L + PW}" y2="{y(FIXED_BUDGET_MS):.1f}" '
           f'stroke="{FIXED}" stroke-width="1.5" stroke-dasharray="7 5"/>')

staircase = []
for i, f in enumerate(frames):
    yy = y(f["deadlineMs"])
    staircase.append(f"{x_left(i):.1f},{yy:.1f}")
    staircase.append(f"{x_left(i + 1):.1f},{yy:.1f}")
svg.append(f'<polyline points="{" ".join(staircase)}" fill="none" stroke="{BUDGET}" stroke-width="2.5" '
           f'stroke-linejoin="round" stroke-linecap="round"/>')

hero = max(range(len(frames)), key=lambda i: frames[i]["totalMs"] if not late_by_own(frames[i]) else 0)
hf = frames[hero]
hx, hy = x_mid(hero), y(hf["totalMs"])
svg.append(f'<line x1="{hx + 12:.1f}" y1="{hy - 4:.1f}" x2="{hx + 96:.1f}" y2="{hy - 38:.1f}" stroke="{INK}" opacity="0.5"/>')
svg.append(f'<text x="{hx + 102:.1f}" y="{hy - 42:.1f}" fill="{TITLE}" font-size="15" font-weight="600">'
           f'{hf["totalMs"]:.1f} ms, on time</text>')
svg.append(f'<text x="{hx + 102:.1f}" y="{hy - 24:.1f}" fill="{SUB}" font-size="13">'
           f'its deadline that frame was {hf["deadlineMs"]:.1f}</text>')

rows = (("by its own deadline", late_by_own), ("by a fixed 16.7 ms", late_by_fixed))
disagreements = 0
for r, (label, judge) in enumerate(rows):
    cy = STRIP_TOP + r * (CELL + 8)
    svg.append(f'<text x="{L - 12}" y="{cy + CELL - 2}" fill="{AXIS}" font-size="12" text-anchor="end">{label}</text>')
    for i, f in enumerate(frames):
        fill = LATE_CELL if judge(f) else ON_TIME_CELL
        svg.append(f'<rect x="{x_mid(i) - bar_w / 2:.1f}" y="{cy}" width="{bar_w:.1f}" height="{CELL}" fill="{fill}" rx="2"/>')
for i, f in enumerate(frames):
    if late_by_own(f) != late_by_fixed(f):
        disagreements += 1
        svg.append(f'<rect x="{x_mid(i) - bar_w / 2 - 3:.1f}" y="{STRIP_TOP - 3}" width="{bar_w + 6:.1f}" '
                   f'height="{2 * CELL + 8 + 6}" fill="none" stroke="{INK}" stroke-width="1.4" rx="3"/>')

svg.append(f'<text x="{L}" y="46" fill="{TITLE}" font-size="27" font-weight="600">The frame budget is not a constant</text>')
svg.append(f'<text x="{L}" y="74" fill="{SUB}" font-size="15">{len(frames)} consecutive frames on a Galaxy S25 Ultra '
           f'pinned to {round(data["refreshRateHz"])} Hz. The refresh rate never changed.</text>')

ly = H - 58


def key(dx, mark, text):
    svg.append(mark(L + dx))
    svg.append(f'<text x="{L + dx + 26}" y="{ly + 4}" fill="{AXIS}" font-size="13">{text}</text>')


key(0, lambda p: f'<line x1="{p}" y1="{ly}" x2="{p + 18}" y2="{ly}" stroke="{BUDGET}" stroke-width="2.5"/>',
    "the deadline the system reported, per frame")
key(340, lambda p: f'<line x1="{p}" y1="{ly}" x2="{p + 18}" y2="{ly}" stroke="{FIXED}" stroke-width="1.5" stroke-dasharray="7 5"/>',
    "a fixed 16.7 ms budget")
key(560, lambda p: f'<rect x="{p}" y="{ly - 6}" width="18" height="12" fill="{LOST}" rx="1"/>',
    "past its own deadline")
key(760, lambda p: f'<rect x="{p}" y="{ly - 7}" width="18" height="14" fill="none" stroke="{INK}" stroke-width="1.4" rx="3"/>',
    f"the {disagreements} frames the two budgets judge differently")
svg.append(f'<text x="{L}" y="{H - 24}" fill="#5c6472" font-size="12">'
           f'{data["window"].capitalize()} of a {data["run"]}, from a FrameHUD session export. github.com/timkrest/FrameHUD</text>')
svg.append("</svg>")

open(os.path.join(HERE, "frame-budget.svg"), "w").write("\n".join(svg))
print(f"{len(frames)} frames, {disagreements} judged differently, hero frame {hero}: "
      f'{hf["totalMs"]:.1f} ms against {hf["deadlineMs"]:.1f}')
