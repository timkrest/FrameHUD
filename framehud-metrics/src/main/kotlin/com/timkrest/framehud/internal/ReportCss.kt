// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

internal val REPORT_CSS = """
    :root { color-scheme: light dark;
      --bg: #f7f7f5; --card: #ffffff; --text: #1c1c1a; --muted: #6d6d68; --line: #e4e4df;
      --ok: #4c9a52; --janky: #c94f42; --budget: #1c1c1a; --caution: #9a6b00; }
    @media (prefers-color-scheme: dark) { :root {
      --bg: #191918; --card: #212120; --text: #ececea; --muted: #9c9c96; --line: #33332f;
      --ok: #5aa860; --janky: #d8695c; --budget: #ececea; --caution: #d0a13e; } }
    * { box-sizing: border-box; }
    body { margin: 0 auto; padding: 24px 16px 8px; max-width: 760px;
      background: var(--bg); color: var(--text);
      font: 15px/1.45 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
    h1 { font-size: 20px; margin: 0 0 4px; }
    h2 { font-size: 13px; margin: 0 0 10px; color: var(--muted);
      text-transform: uppercase; letter-spacing: 0.06em; }
    .meta { color: var(--muted); margin: 0 0 8px; }
    header { margin-bottom: 16px; }
    section { background: var(--card); border: 1px solid var(--line); border-radius: 10px;
      padding: 14px 16px; margin-bottom: 12px; }
    .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
      gap: 12px; background: none; border: none; padding: 0; }
    .tile { background: var(--card); border: 1px solid var(--line); border-radius: 10px;
      padding: 12px 14px; }
    .tile strong { display: block; font-size: 22px; font-weight: 600; }
    .tile span { color: var(--muted); font-size: 12px; text-transform: uppercase;
      letter-spacing: 0.06em; }
    table { border-collapse: collapse; width: 100%; }
    th, td { text-align: left; padding: 5px 16px 5px 0; vertical-align: top;
      border-bottom: 1px solid var(--line); font-weight: normal; }
    tr:last-child th, tr:last-child td { border-bottom: none; }
    th { color: var(--muted); width: 200px; }
    tr > th:not(:first-child) { width: auto; }
    code { background: var(--bg); border: 1px solid var(--line); border-radius: 5px;
      padding: 1px 6px; font-size: 13px; }
    svg { display: block; width: 100%; height: 120px; margin-top: 8px; }
    svg .ok { fill: var(--ok); }
    svg .janky { fill: var(--janky); }
    svg .budget { stroke: var(--budget); stroke-width: 1; opacity: 0.55; }
    svg .trigger { stroke: var(--caution); stroke-width: 2; }
    .caution { color: var(--caution); margin: 10px 0 0; }
    footer { color: var(--muted); font-size: 12px; padding: 8px 0 24px; }
""".trimIndent()
