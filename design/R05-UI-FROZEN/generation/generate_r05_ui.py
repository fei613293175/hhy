from __future__ import annotations

import asyncio
import argparse
import csv
import hashlib
import json
import math
import os
import re
import shutil
import textwrap
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFont
from playwright.async_api import async_playwright

SCRIPT_PATH = Path(__file__).resolve()
PACKAGE_ROOT = SCRIPT_PATH.parents[3]

parser = argparse.ArgumentParser(description='Regenerate the R05 identity visual supplement package deterministically.')
parser.add_argument('--source', type=Path, default=PACKAGE_ROOT / 'REFERENCE_ONLY',
                    help='Extracted Codex supplement source or this package REFERENCE_ONLY directory.')
parser.add_argument('--output', type=Path, required=True,
                    help='A NEW output directory. It must be disjoint from the source and this package.')
args = parser.parse_args()
SRC = args.source.expanduser().resolve()
OUT_ROOT = args.output.expanduser().resolve()

if not SRC.is_dir():
    raise SystemExit(f'Source directory does not exist: {SRC}')
if OUT_ROOT == SRC or OUT_ROOT in SRC.parents or SRC in OUT_ROOT.parents:
    raise SystemExit('Source and output directories must be disjoint.')
if OUT_ROOT == PACKAGE_ROOT or OUT_ROOT in PACKAGE_ROOT.parents or PACKAGE_ROOT in OUT_ROOT.parents:
    raise SystemExit('Output must be outside the current packaged deliverable to prevent self-deletion.')

REF = OUT_ROOT / 'REFERENCE_ONLY'
OVR = OUT_ROOT / 'EDITABLE_OVERLAY'
FROZEN = OVR / 'R05-UI-FROZEN'
IMAGES = FROZEN / 'images'
SPECS = FROZEN / 'specs'
GEN = FROZEN / 'generation'
CATALOGS = OVR / 'catalogs'
PAGE_SPECS = OVR / 'page-specs'

if OUT_ROOT.exists():
    shutil.rmtree(OUT_ROOT)
REF.mkdir(parents=True)
OVR.mkdir(parents=True)
FROZEN.mkdir(parents=True)
IMAGES.mkdir(parents=True)
SPECS.mkdir(parents=True)
GEN.mkdir(parents=True)
CATALOGS.mkdir(parents=True)
PAGE_SPECS.mkdir(parents=True)

# Preserve the Codex-provided supplement package exactly as a reference layer.
for p in SRC.rglob('*'):
    target = REF / p.relative_to(SRC)
    if p.is_dir():
        target.mkdir(parents=True, exist_ok=True)
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(p, target)

TOKENS_PATH = SRC / 'tokens/hhy_design_tokens_v1.2.2.json'
TOKENS = json.loads(TOKENS_PATH.read_text('utf-8'))

C = {
    'primary': '#1677FF', 'primary_dark': '#0B63CE', 'secondary': '#13B8A6',
    'page': '#F5F7FA', 'surface': '#FFFFFF', 'soft_blue': '#EEF5FF',
    'text': '#182230', 'text2': '#667085', 'text3': '#98A2B3', 'border': '#E4E7EC',
    'success': '#12B76A', 'warning': '#F79009', 'error': '#F04438',
    'soft_success': '#ECFDF3', 'soft_warning': '#FFFAEB', 'soft_error': '#FEF3F2',
    'dark': '#071A3A', 'dark2': '#102F68',
}

FONT_FAMILY = '"Noto Sans CJK SC", "Microsoft YaHei", system-ui, sans-serif'

SVG = {
    'back': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 18l-6-6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    'chev': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    'check': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.2 4.2L19 7" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    'shield': '<svg viewBox="0 0 64 64" aria-hidden="true"><path d="M32 5l22 8v16c0 14-9 24-22 30C19 53 10 43 10 29V13l22-8z" fill="currentColor" opacity=".22"/><path d="M32 10l17 6v13c0 10.8-6.6 19-17 24-10.4-5-17-13.2-17-24V16l17-6z" fill="currentColor" opacity=".3"/><path d="M23 31l6 6 12-13" fill="none" stroke="white" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></svg>',
    'person': '<svg viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="23" r="11" fill="currentColor" opacity=".9"/><path d="M13 55c2-12 9-19 19-19s17 7 19 19" fill="currentColor" opacity=".45"/></svg>',
    'clock': '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 7v5l3 2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    'warning': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l10 18H2L12 3z" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 9v5m0 3h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    'refresh': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 11a8 8 0 10-2 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M20 5v6h-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>',
    'lock': '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="10" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M8 10V7a4 4 0 018 0v3" fill="none" stroke="currentColor" stroke-width="2"/></svg>',
    'camera': '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h4l2-2h4l2 2h4v12H4V7z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><circle cx="12" cy="13" r="3.5" fill="none" stroke="currentColor" stroke-width="2"/></svg>',
    'face': '<svg viewBox="0 0 120 120" aria-hidden="true"><path d="M28 16H16v12M92 16h12v12M28 104H16V92M92 104h12V92" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/><circle cx="60" cy="48" r="20" fill="none" stroke="currentColor" stroke-width="4"/><path d="M31 93c6-20 17-30 29-30s23 10 29 30" fill="none" stroke="currentColor" stroke-width="4" stroke-linecap="round"/></svg>',
    'empty': '<svg viewBox="0 0 96 96" aria-hidden="true"><rect x="18" y="24" width="60" height="48" rx="10" fill="currentColor" opacity=".12"/><path d="M28 38h40M28 49h24" stroke="currentColor" stroke-width="4" stroke-linecap="round" opacity=".65"/><circle cx="68" cy="67" r="13" fill="white" stroke="currentColor" stroke-width="3"/><path d="M68 61v12M62 67h12" stroke="currentColor" stroke-width="3" stroke-linecap="round"/></svg>',
    'offline': '<svg viewBox="0 0 96 96" aria-hidden="true"><path d="M18 39c17-16 43-16 60 0M29 51c11-10 27-10 38 0M42 64c4-4 8-4 12 0" fill="none" stroke="currentColor" stroke-width="5" stroke-linecap="round" opacity=".7"/><path d="M18 18l60 60" stroke="currentColor" stroke-width="6" stroke-linecap="round"/></svg>',
}

BASE_CSS = f'''
*{{box-sizing:border-box}} html,body{{margin:0;width:100%;height:100%;font-family:{FONT_FAMILY};color:{C['text']};-webkit-font-smoothing:antialiased}} body{{background:{C['page']}}}
button,input,textarea,select{{font:inherit}} button{{cursor:default}}
svg{{display:block;width:100%;height:100%}}
.mobile{{width:360px;height:800px;overflow:hidden;background:{C['page']};position:relative}}
.statusbar{{height:24px;background:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 16px;font-size:11px;font-weight:600;color:#111827}}
.status-icons{{display:flex;gap:5px;align-items:center}} .bars{{display:flex;gap:2px;align-items:flex-end;height:12px}} .bars i{{display:block;width:2px;background:#111827;border-radius:2px}} .bars i:nth-child(1){{height:4px}} .bars i:nth-child(2){{height:6px}} .bars i:nth-child(3){{height:9px}} .bars i:nth-child(4){{height:12px}}
.wifi{{width:14px;height:10px;border-top:2px solid #111827;border-radius:50%;position:relative}} .wifi:after{{content:'';position:absolute;width:4px;height:4px;border-radius:50%;background:#111827;left:5px;top:5px}}
.battery{{width:20px;height:10px;border:1.5px solid #111827;border-radius:2px;position:relative}} .battery:before{{content:'';position:absolute;right:-3px;top:2px;width:2px;height:4px;background:#111827;border-radius:1px}} .battery:after{{content:'';position:absolute;left:2px;top:2px;width:13px;height:4px;background:#111827;border-radius:1px}}
.appbar{{height:56px;background:#fff;display:flex;align-items:center;justify-content:center;position:relative;border-bottom:.5px solid #F2F4F7}}
.appbar h1{{font-size:18px;line-height:26px;margin:0;font-weight:600}} .back{{position:absolute;left:8px;top:4px;width:48px;height:48px;border:0;background:transparent;color:{C['text']};padding:12px}}
.page-scroll{{height:720px;overflow:hidden;padding:16px;position:relative}}
.stack{{display:flex;flex-direction:column;gap:12px}}
.card{{background:#fff;border-radius:16px;padding:16px;box-shadow:0 2px 8px #10182814;border:1px solid #F2F4F7}}
.status-card{{background:linear-gradient(135deg,#1677FF,#39A0FF);color:#fff;border-radius:16px;padding:16px;min-height:104px;display:flex;justify-content:space-between;align-items:center;box-shadow:0 8px 22px #1677FF2B}}
.status-card.warning{{background:linear-gradient(135deg,#F79009,#FFB020)}} .status-card.error{{background:linear-gradient(135deg,#F04438,#FF7A59)}} .status-card.success{{background:linear-gradient(135deg,#1677FF,#39A0FF)}}
.status-copy h2{{font-size:18px;line-height:26px;margin:0 0 5px;font-weight:700}} .status-copy p{{font-size:12px;line-height:18px;margin:0;color:#FFFFFFE6;max-width:220px}}
.status-visual{{width:72px;height:72px;color:#fff;flex:0 0 auto}}
.section-title{{font-size:16px;line-height:24px;font-weight:600;margin:0 0 12px}} .body{{font-size:14px;line-height:22px;color:{C['text2']}}} .caption{{font-size:12px;line-height:18px;color:{C['text2']}}} .muted{{color:{C['text3']}}}
.list-row{{min-height:64px;display:flex;align-items:center;gap:12px;border-bottom:.5px solid {C['border']}}} .list-row:last-child{{border-bottom:0}} .row-icon{{width:32px;height:32px;border-radius:10px;background:{C['soft_blue']};color:{C['primary']};display:grid;place-items:center;padding:7px}} .row-icon svg{{width:18px;height:18px}} .row-main{{flex:1;min-width:0}} .row-main strong{{display:block;font-size:14px;line-height:21px;font-weight:600}} .row-main small{{display:block;font-size:12px;line-height:18px;color:{C['text2']};margin-top:2px}} .chev{{width:20px;height:20px;color:{C['text3']}}}
.primary-btn,.secondary-btn,.danger-btn{{height:48px;border-radius:12px;border:0;font-size:15px;font-weight:600;width:100%;display:flex;align-items:center;justify-content:center;gap:8px}}
.primary-btn{{background:{C['primary']};color:#fff;box-shadow:0 6px 16px #1677FF29}} .secondary-btn{{background:#fff;color:{C['primary']};border:1px solid {C['primary']}}} .danger-btn{{background:#fff;color:{C['error']};border:1px solid #FDA29B}}
.button-row{{display:flex;gap:12px}} .button-row .secondary-btn{{flex:0 0 108px}} .button-row .primary-btn{{flex:1}}
.field{{display:flex;flex-direction:column;gap:7px}} .field label{{font-size:13px;line-height:20px;font-weight:600}} .input{{height:52px;border:1px solid {C['border']};border-radius:12px;background:#fff;padding:0 14px;font-size:14px;color:{C['text']};display:flex;align-items:center}} .input.placeholder{{color:{C['text3']}}} .input.error{{border-color:{C['error']};box-shadow:0 0 0 3px #F0443814}} .field-error{{font-size:12px;line-height:18px;color:{C['error']}}} .textarea{{min-height:92px;padding:12px 14px;align-items:flex-start}}
.notice{{border-radius:14px;padding:12px 14px;display:flex;gap:10px;font-size:13px;line-height:20px}} .notice.info{{background:{C['soft_blue']};color:{C['primary_dark']}}} .notice.warn{{background:{C['soft_warning']};color:#B54708}} .notice.danger{{background:{C['soft_error']};color:#B42318}} .notice.success{{background:{C['soft_success']};color:#027A48}} .notice-icon{{width:20px;height:20px;flex:0 0 auto}}
.checkbox-row{{display:flex;align-items:flex-start;gap:10px;padding:2px 0}} .check-box{{width:20px;height:20px;border:1.5px solid {C['text3']};border-radius:4px;background:#fff;flex:0 0 auto;margin-top:1px}} .check-box.checked{{background:{C['primary']};border-color:{C['primary']};color:#fff;padding:2px}}
.pill{{display:inline-flex;align-items:center;height:28px;border-radius:999px;padding:0 10px;font-size:12px;font-weight:600;background:{C['soft_blue']};color:{C['primary']}}} .pill.success{{background:{C['soft_success']};color:#027A48}} .pill.warn{{background:{C['soft_warning']};color:#B54708}} .pill.error{{background:{C['soft_error']};color:#B42318}}
.info-grid{{display:grid;grid-template-columns:92px 1fr;gap:12px 10px;font-size:13px;line-height:20px}} .info-grid .label{{color:{C['text3']}}} .info-grid .value{{color:{C['text']};font-weight:500}}
.progress-steps{{display:flex;flex-direction:column;gap:10px}} .step{{display:flex;align-items:center;gap:10px;font-size:13px}} .step-dot{{width:22px;height:22px;border-radius:50%;display:grid;place-items:center;background:{C['soft_blue']};color:{C['primary']};font-size:12px;font-weight:700}} .step.done .step-dot{{background:{C['success']};color:#fff}}
.overlay{{position:absolute;inset:0;background:#1822307A;display:flex;align-items:center;justify-content:center;padding:24px;z-index:20}} .dialog{{width:328px;max-height:560px;background:#fff;border-radius:20px;box-shadow:0 12px 32px #10182833;padding:24px;display:flex;flex-direction:column;gap:16px}} .dialog h3{{font-size:20px;line-height:28px;margin:0;font-weight:600}} .dialog-body{{font-size:13px;line-height:21px;color:{C['text2']};max-height:280px;overflow:hidden}} .dialog-actions{{display:flex;gap:12px}} .dialog-actions .secondary-btn{{width:84px;flex:0 0 84px}} .dialog-actions .primary-btn{{flex:1}}
.spinner{{width:36px;height:36px;border:3px solid #D1E9FF;border-top-color:{C['primary']};border-radius:50%;animation:spin 1s linear infinite}} @keyframes spin{{to{{transform:rotate(360deg)}}}}
.skeleton{{background:linear-gradient(90deg,#F2F4F7 25%,#EAECF0 37%,#F2F4F7 63%);background-size:400% 100%;animation:shine 1.4s ease infinite;border-radius:8px}} @keyframes shine{{0%{{background-position:100% 0}}100%{{background-position:-100% 0}}}}
.face-panel{{height:360px;border-radius:20px;background:linear-gradient(180deg,#DCEBFF,#B9D8FF);position:relative;overflow:hidden;display:flex;align-items:center;justify-content:center;color:{C['primary']};box-shadow:inset 0 0 0 1px #B2CCFF}}
.face-panel.dark{{background:radial-gradient(circle at 50% 40%,#224E88 0,#0C244A 55%,#071A3A 100%);color:#9BC5FF}}
.face-frame{{width:240px;height:240px;position:relative;color:currentColor}} .face-guide{{position:absolute;top:18px;left:50%;transform:translateX(-50%);height:28px;border-radius:999px;padding:0 12px;background:#FFFFFFE8;color:{C['text']};display:flex;align-items:center;font-size:12px;font-weight:600}}
.face-tip{{position:absolute;bottom:18px;left:18px;right:18px;background:#FFFFFFE8;border-radius:12px;padding:10px 12px;text-align:center;font-size:12px;color:{C['text2']}}} .face-tip.error{{background:{C['soft_error']};color:#B42318}}
.web{{width:360px;height:800px;overflow:hidden;background:linear-gradient(180deg,{C['dark']} 0,{C['dark2']} 235px,{C['page']} 235px);position:relative}} .web-head{{height:188px;padding:28px 20px;color:#fff}} .brand{{display:flex;align-items:center;gap:10px;font-weight:700;font-size:18px}} .brand-mark{{width:36px;height:36px;border-radius:12px;background:linear-gradient(135deg,#39A0FF,#13B8A6);display:grid;place-items:center;color:#fff}} .web-head h1{{font-size:25px;line-height:34px;margin:26px 0 8px}} .web-head p{{font-size:13px;line-height:21px;color:#D1E9FF;margin:0}} .web-card{{margin:-6px 16px 0;background:#fff;border-radius:20px;padding:28px 20px;box-shadow:0 10px 32px #102F6824;text-align:center;min-height:410px}} .web-icon{{width:88px;height:88px;border-radius:28px;margin:0 auto 18px;display:grid;place-items:center;color:{C['primary']};background:{C['soft_blue']};padding:20px}} .web-icon.success{{background:{C['soft_success']};color:{C['success']}}} .web-icon.warn{{background:{C['soft_warning']};color:{C['warning']}}} .web-icon.error{{background:{C['soft_error']};color:{C['error']}}} .web-card h2{{font-size:20px;line-height:28px;margin:0 0 8px}} .web-card p{{font-size:14px;line-height:22px;color:{C['text2']};margin:0 auto 24px;max-width:280px}} .web-meta{{background:{C['page']};border-radius:14px;padding:14px;text-align:left;margin:0 0 20px}} .web-meta div{{display:flex;justify-content:space-between;font-size:13px;line-height:22px}} .web-meta span:first-child{{color:{C['text3']}}}
'''

ADMIN_CSS = f'''
.admin{{width:1440px;height:900px;background:{C['page']};display:flex;overflow:hidden;font-family:{FONT_FAMILY};color:{C['text']}}} .sidebar{{width:240px;background:#0B1F3A;color:#fff;padding:18px 14px;display:flex;flex-direction:column}} .admin-logo{{height:50px;display:flex;align-items:center;gap:10px;padding:0 10px;font-size:18px;font-weight:700}} .admin-logo-mark{{width:34px;height:34px;border-radius:11px;background:linear-gradient(135deg,#1677FF,#13B8A6);display:grid;place-items:center}}
.nav-group{{margin-top:18px}} .nav-label{{padding:0 12px 8px;color:#8EA7C7;font-size:12px}} .nav-item{{height:44px;border-radius:10px;display:flex;align-items:center;gap:12px;padding:0 12px;color:#C8D7EA;font-size:14px;margin-bottom:4px}} .nav-item.active{{background:#1677FF;color:#fff;font-weight:600}} .nav-dot{{width:18px;height:18px;border-radius:6px;border:1.5px solid currentColor;opacity:.9}}
.admin-main{{flex:1;min-width:0}} .admin-header{{height:56px;background:#fff;border-bottom:1px solid {C['border']};display:flex;align-items:center;justify-content:space-between;padding:0 24px}} .crumb{{font-size:13px;color:{C['text2']}}} .admin-user{{display:flex;align-items:center;gap:10px;font-size:13px}} .avatar{{width:32px;height:32px;border-radius:50%;background:{C['soft_blue']};color:{C['primary']};display:grid;place-items:center;font-weight:700}}
.admin-content{{height:844px;padding:24px;overflow:hidden}} .admin-page-head{{display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:20px}} .admin-page-head h1{{font-size:20px;line-height:28px;margin:0 0 5px}} .admin-page-head p{{font-size:13px;line-height:20px;margin:0;color:{C['text2']}}} .admin-actions{{display:flex;gap:10px}} .admin-btn{{height:40px;border-radius:10px;padding:0 16px;border:1px solid {C['border']};background:#fff;color:{C['text']};font-size:14px;font-weight:600}} .admin-btn.primary{{background:{C['primary']};border-color:{C['primary']};color:#fff}} .admin-btn.danger{{color:{C['error']};border-color:#FDA29B;background:#fff}}
.admin-card{{background:#fff;border:1px solid {C['border']};border-radius:12px;padding:20px;box-shadow:0 2px 8px #1018280F}} .metrics{{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px}} .metric{{padding:16px 18px}} .metric small{{color:{C['text2']};font-size:12px}} .metric strong{{display:block;font-size:24px;line-height:32px;margin-top:6px}} .metric span{{font-size:12px;color:{C['text3']}}} .filter-card{{margin-bottom:16px}} .filter-row{{display:flex;gap:12px;align-items:center}} .admin-input,.admin-select{{height:40px;border:1px solid {C['border']};border-radius:10px;background:#fff;padding:0 12px;font-size:13px;color:{C['text']}}} .admin-input{{width:260px}} .admin-select{{width:150px}} .filter-spacer{{flex:1}}
.table-card{{padding:0;overflow:hidden}} .table-toolbar{{height:54px;padding:0 18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid {C['border']}}} .table-toolbar strong{{font-size:15px}} .table-toolbar span{{font-size:12px;color:{C['text2']}}} table{{width:100%;border-collapse:collapse;table-layout:fixed}} th{{height:44px;background:#F9FAFB;color:{C['text2']};font-size:12px;font-weight:600;text-align:left;padding:0 14px;border-bottom:1px solid {C['border']}}} td{{height:58px;font-size:13px;padding:0 14px;border-bottom:1px solid #F2F4F7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}} tr:last-child td{{border-bottom:0}} .user-cell{{display:flex;align-items:center;gap:10px}} .user-badge{{width:34px;height:34px;border-radius:10px;background:{C['soft_blue']};color:{C['primary']};display:grid;place-items:center;font-weight:700}} .status-tag{{display:inline-flex;align-items:center;height:26px;border-radius:999px;padding:0 9px;font-size:12px;font-weight:600;background:{C['soft_blue']};color:{C['primary']}}} .status-tag.success{{background:{C['soft_success']};color:#027A48}} .status-tag.warn{{background:{C['soft_warning']};color:#B54708}} .status-tag.error{{background:{C['soft_error']};color:#B42318}} .link{{color:{C['primary']};font-weight:600}}
.pagination{{height:56px;display:flex;align-items:center;justify-content:flex-end;gap:8px;padding:0 18px;border-top:1px solid {C['border']}}} .page-btn{{width:32px;height:32px;border:1px solid {C['border']};background:#fff;border-radius:8px;display:grid;place-items:center;font-size:12px}} .page-btn.active{{background:{C['primary']};color:#fff;border-color:{C['primary']}}} .empty-admin,.error-admin{{height:470px;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center}} .empty-admin .big-icon,.error-admin .big-icon{{width:88px;height:88px;color:{C['primary']};margin-bottom:18px}} .error-admin .big-icon{{color:{C['error']}}} .empty-admin h2,.error-admin h2{{font-size:18px;margin:0 0 7px}} .empty-admin p,.error-admin p{{font-size:13px;color:{C['text2']};max-width:360px;margin:0 0 20px}}
.detail-grid{{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:16px}} .detail-summary{{display:grid;grid-template-columns:1.4fr .9fr .9fr;gap:14px;margin-bottom:16px}} .summary-card{{padding:18px}} .summary-user{{display:flex;gap:12px;align-items:center}} .summary-user .avatar{{width:48px;height:48px;border-radius:14px}} .summary-user strong{{display:block;font-size:15px}} .summary-user small{{display:block;font-size:12px;color:{C['text2']};margin-top:3px}} .summary-card label{{display:block;color:{C['text2']};font-size:12px;margin-bottom:8px}} .summary-card b{{font-size:16px}} .tabs{{height:48px;display:flex;gap:26px;border-bottom:1px solid {C['border']};padding:0 20px}} .tab{{height:48px;display:flex;align-items:center;font-size:13px;color:{C['text2']};position:relative}} .tab.active{{color:{C['primary']};font-weight:600}} .tab.active:after{{content:'';position:absolute;left:0;right:0;bottom:-1px;height:2px;background:{C['primary']};border-radius:2px}}
.detail-section{{padding:20px}} .section-head{{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}} .section-head h2{{font-size:16px;margin:0}} .kv-grid{{display:grid;grid-template-columns:140px 1fr 140px 1fr;gap:14px 18px;font-size:13px}} .kv-grid .k{{color:{C['text2']}}} .kv-grid .v{{font-weight:500}} .evidence{{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:18px}} .evidence-box{{height:190px;background:{C['page']};border:1px dashed #B2CCFF;border-radius:12px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:{C['text2']};font-size:12px;gap:10px}} .evidence-face{{width:72px;height:72px;color:{C['primary']}}} .side-card{{padding:20px}} .side-card h2{{font-size:16px;margin:0 0 8px}} .side-card p{{font-size:12px;line-height:19px;color:{C['text2']};margin:0 0 16px}} .admin-field{{display:flex;flex-direction:column;gap:6px;margin-bottom:14px}} .admin-field label{{font-size:12px;font-weight:600}} .admin-textarea{{min-height:88px;border:1px solid {C['border']};border-radius:10px;padding:10px 12px;font-size:13px;color:{C['text2']}}} .choice-row{{display:flex;gap:8px;flex-wrap:wrap}} .choice{{height:34px;border:1px solid {C['border']};border-radius:9px;padding:0 12px;display:flex;align-items:center;font-size:12px}} .choice.active{{border-color:{C['primary']};background:{C['soft_blue']};color:{C['primary']};font-weight:600}}
.admin-overlay{{position:absolute;inset:56px 0 0 240px;background:#1822307A;z-index:20;display:flex;align-items:center;justify-content:center}} .admin-dialog{{width:520px;background:#fff;border-radius:16px;padding:24px;box-shadow:0 12px 32px #10182833}} .admin-dialog h3{{font-size:18px;margin:0 0 8px}} .admin-dialog p{{font-size:13px;line-height:20px;color:{C['text2']};margin:0 0 18px}} .dialog-form-grid{{display:grid;grid-template-columns:1fr 160px;gap:12px}} .admin-dialog-actions{{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}}
.conflict-banner{{background:{C['soft_warning']};border:1px solid #FEDF89;border-radius:12px;padding:14px 16px;display:flex;align-items:flex-start;justify-content:space-between;margin-bottom:16px}} .conflict-banner strong{{display:block;font-size:14px;color:#B54708}} .conflict-banner p{{font-size:12px;line-height:18px;color:#B54708;margin:3px 0 0}}
@media(max-width:1280px){{.sidebar{{width:88px}} .admin-logo span,.nav-label,.nav-item span{{display:none}} .nav-item{{justify-content:center}} .admin-overlay{{left:88px}} .detail-grid{{grid-template-columns:1fr}} .side-card{{display:grid;grid-template-columns:1fr 1fr;gap:16px}} .side-card h2,.side-card p{{grid-column:1/-1}}}}
'''

@dataclass
class Shot:
    page_id: str
    order: int
    state: str
    filename: str
    kind: str  # mobile/web/admin
    html: str
    width: int
    height: int
    scale: int = 1


def icon(name: str, cls: str = '') -> str:
    return f'<span class="{cls}">{SVG[name]}</span>'


def mobile_shell(title: str, body: str, overlay: str = '') -> str:
    return f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>{BASE_CSS}</style></head><body><main class="mobile"><div class="statusbar"><span>9:41</span><div class="status-icons"><div class="bars"><i></i><i></i><i></i><i></i></div><div class="wifi"></div><div class="battery"></div></div></div><header class="appbar"><button class="back">{SVG['back']}</button><h1>{title}</h1></header><section class="page-scroll">{body}</section>{overlay}</main></body></html>'''


def web_shell(body: str) -> str:
    return f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>{BASE_CSS}</style></head><body>{body}</body></html>'''


def admin_shell(content: str, overlay: str = '', active: str = '实名认证') -> str:
    navs = ['数据总览','用户管理','内容管理','审核中心','实名认证','订单与支付','奖励与提现','系统配置']
    nav_html = ''.join(f'<div class="nav-item {"active" if n==active else ""}"><i class="nav-dot"></i><span>{n}</span></div>' for n in navs)
    return f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><style>{BASE_CSS}{ADMIN_CSS}</style></head><body><main class="admin"><aside class="sidebar"><div class="admin-logo"><div class="admin-logo-mark">合</div><span>合伙云 Pro</span></div><div class="nav-group"><div class="nav-label">运营管理</div>{nav_html}</div></aside><section class="admin-main"><header class="admin-header"><div class="crumb">运营后台 / 实名认证</div><div class="admin-user"><span>安全运营</span><div class="avatar">管</div></div></header><div class="admin-content">{content}</div></section>{overlay}</main></body></html>'''


def status_card(title: str, subtitle: str, tone: str='success', visual: str='shield') -> str:
    return f'<div class="status-card {tone}"><div class="status-copy"><h2>{title}</h2><p>{subtitle}</p></div>{icon(visual,"status-visual")}</div>'


def row(title: str, desc: str, ico: str='check', right: str='') -> str:
    right_html = '' if right == '__none__' else (f'<span class="pill {right.split("|")[0]}">{right.split("|")[1]}</span>' if '|' in right else (f'<span class="caption">{right}</span>' if right else icon('chev','chev')))
    return f'<div class="list-row"><div class="row-icon">{SVG.get(ico,SVG["check"])}</div><div class="row-main"><strong>{title}</strong><small>{desc}</small></div>{right_html}</div>'


def mobile_identity_home(state: str) -> str:
    if state == '未认证':
        body = f'''<div class="stack">{status_card('尚未完成实名认证','完成认证后可提升账号可信度，并使用需要实名的业务能力','success','person')}<div class="card"><h3 class="section-title">认证前请准备</h3>{row('本人有效身份证件','请填写与证件一致的真实信息','check','__none__')}{row('可正常使用的手机相机','活体检测需要使用前置相机','camera','__none__')}{row('由账号本人完成检测','请勿由他人代为操作','shield','__none__')}</div><button class="primary-btn">开始认证</button><p class="caption" style="text-align:center;margin:0 12px">身份信息将按隐私政策用于完成实名认证</p></div>'''
    elif state == '认证中':
        body = f'''<div class="stack">{status_card('认证处理中','身份信息与活体结果正在核验，请耐心等待','success','clock')}<div class="card"><h3 class="section-title">当前进度</h3><div class="progress-steps"><div class="step done"><span class="step-dot">{SVG['check']}</span><span>身份信息已提交</span></div><div class="step done"><span class="step-dot">{SVG['check']}</span><span>活体检测已完成</span></div><div class="step"><span class="step-dot">3</span><span>等待核验结果</span></div></div></div><button class="secondary-btn">刷新认证状态</button><div class="notice info">{icon('clock','notice-icon')}<span>核验期间无需重复提交，结果更新后本页会显示最新状态。</span></div></div>'''
    else:
        body = f'''<div class="stack">{status_card('已完成实名认证','认证信息已通过核验，账号实名状态正常','success','shield')}<div class="card"><h3 class="section-title">认证信息</h3><div class="info-grid"><div class="label">真实姓名</div><div class="value">李*</div><div class="label">身份证号</div><div class="value">440***********0098</div><div class="label">认证时间</div><div class="value">2026-07-20 09:18</div></div></div><div class="card" style="padding-top:4px;padding-bottom:4px">{row('查看认证信息','认证资料不可自行修改','shield')}</div><div class="notice success">{icon('check','notice-icon')}<span>认证信息仅用于账号安全与相关业务核验。</span></div></div>'''
    return mobile_shell('实名认证', body)


def identity_form_body(mode: str) -> tuple[str,str]:
    real = '<div class="input placeholder">请输入本人真实姓名</div>'
    ident = '<div class="input placeholder">请输入本人身份证号</div>'
    errors = ''
    notice = ''
    consent_checked = False
    retry = ''
    if mode == '字段错误':
        notice = f'<div class="notice danger">{icon("warning","notice-icon")}<span>请检查填写的信息后再提交</span></div>'
        real = '<div class="input error"></div><span class="field-error">请输入真实姓名</span>'
        ident = '<div class="input error">123456</div><span class="field-error">身份证号格式不正确</span>'
    if mode == '授权说明弹层':
        real = '<div class="input">林晓宁</div>'
        ident = '<div class="input">4401••••••••0018</div>'
        consent_checked = True
    if mode == '网络失败重试':
        notice = f'<div class="notice danger">{icon("warning","notice-icon")}<span>实名认证授权说明暂时无法加载，请检查网络后重试。</span></div>'
        retry = '<button class="secondary-btn">重新加载授权说明</button>'
    consent = f'''<div class="checkbox-row"><span class="check-box {"checked" if consent_checked else ""}">{SVG['check'] if consent_checked else ''}</span><div class="body">我已阅读并同意 <span style="color:{C['primary']};font-weight:600">《实名认证授权说明》</span></div></div>'''
    body = f'''<div class="stack"><div><h2 style="font-size:20px;line-height:28px;margin:2px 0 6px">请填写本人真实信息</h2><p class="body" style="margin:0">信息提交后不可自行修改，请仔细核对。</p></div>{notice}<div class="card stack"><div class="field"><label>真实姓名</label>{real}</div><div class="field"><label>身份证号</label>{ident}</div>{consent}</div>{retry}<button class="primary-btn" {"style=\"opacity:.38\"" if mode=='网络失败重试' else ''}>提交并开始活体检测</button><p class="caption" style="text-align:center;margin:0 8px">请确认姓名和证件号码准确无误</p></div>'''
    overlay = ''
    if mode == '授权说明弹层':
        overlay = f'''<div class="overlay"><div class="dialog"><h3>实名认证授权说明</h3><div class="dialog-body"><p>为完成实名认证，我们将核验您提交的身份信息，并在您授权后进行活体检测。</p><p>相关信息仅用于身份核验、账号安全和依法需要的业务场景。请确认由本人操作并提交真实、有效的信息。</p><p>认证结果将按照隐私政策进行保存和保护。</p></div><button class="primary-btn">我已阅读</button></div></div>'''
    return body, overlay


def mobile_identity_form(mode: str) -> str:
    body, overlay = identity_form_body(mode)
    return mobile_shell('填写身份信息', body, overlay)


def mobile_liveness(mode: str) -> str:
    if mode == '准备':
        panel = f'''<div class="face-panel"><div class="face-guide">准备开始活体检测</div>{icon('face','face-frame')}<div class="face-tip">请在光线充足、环境安静的位置完成检测</div></div>'''
        body = f'''<div class="stack"><div class="pill">步骤 2 / 2</div>{panel}<div class="card"><h3 class="section-title">检测前请确认</h3>{row('保持面部清晰可见','请摘下口罩、帽子或遮挡物','person','__none__')}{row('正对屏幕完成动作','根据页面提示缓慢完成','camera','__none__')}</div><button class="primary-btn">开始检测</button></div>'''
        return mobile_shell('活体检测', body)
    if mode == '相机授权':
        panel = f'''<div class="face-panel"><div class="face-guide">相机尚未授权</div>{icon('camera','face-frame')}<div class="face-tip">允许使用相机后才能继续</div></div>'''
        body = f'''<div class="stack"><div class="pill">步骤 2 / 2</div>{panel}<div class="notice info">{icon('lock','notice-icon')}<span>相机画面仅用于本次活体检测。</span></div></div>'''
        overlay = f'''<div class="overlay"><div class="dialog"><div style="width:52px;height:52px;border-radius:16px;background:{C['soft_blue']};color:{C['primary']};padding:13px">{SVG['camera']}</div><h3>需要相机权限</h3><div class="dialog-body">请允许合伙云使用相机，以便由本人完成活体检测。您可以稍后在系统设置中修改权限。</div><div class="dialog-actions"><button class="secondary-btn">暂不</button><button class="primary-btn">允许使用相机</button></div></div></div>'''
        return mobile_shell('活体检测', body, overlay)
    if mode == '加载':
        panel = f'''<div class="face-panel"><div class="spinner"></div><div style="position:absolute;top:220px;font-size:14px;font-weight:600;color:{C['text']}">正在启动安全检测</div><div class="face-tip">请保持页面开启，不要退出</div></div>'''
        body = f'''<div class="stack"><div class="pill">步骤 2 / 2</div>{panel}<button class="secondary-btn">取消检测</button></div>'''
        return mobile_shell('活体检测', body)
    if mode == '处理中':
        panel = f'''<div class="face-panel dark"><div class="face-guide">检测中 · 请保持正脸</div>{icon('face','face-frame')}<div style="position:absolute;left:28px;right:28px;bottom:68px;height:4px;background:#FFFFFF30;border-radius:999px;overflow:hidden"><div style="width:66%;height:100%;background:#39A0FF"></div></div><div class="face-tip">请缓慢眨眼，并保持面部在取景框内</div></div>'''
        body = f'''<div class="stack"><div class="pill">步骤 2 / 2</div>{panel}<div class="notice info">{icon('camera','notice-icon')}<span>请勿切换应用或遮挡面部，检测完成后将自动继续。</span></div></div>'''
        return mobile_shell('活体检测', body)
    panel = f'''<div class="face-panel" style="background:#FEF3F2;color:{C['error']};box-shadow:inset 0 0 0 1px #FDA29B"><div class="face-guide" style="color:#B42318;background:#FFF">检测未完成</div>{icon('warning','face-frame')}<div class="face-tip error">未能识别清晰人脸，请调整光线和距离后重试</div></div>'''
    body = f'''<div class="stack"><div class="pill error">需要重新检测</div>{panel}<div class="button-row"><button class="secondary-btn">返回</button><button class="primary-btn">重新检测</button></div><p class="caption" style="text-align:center">多次失败时可稍后再试</p></div>'''
    return mobile_shell('活体检测', body)


def result_status(mode: str) -> str:
    data_card = '''<div class="card"><h3 class="section-title">认证信息</h3><div class="info-grid"><div class="label">真实姓名</div><div class="value">李*</div><div class="label">身份证号</div><div class="value">440***********0098</div></div></div>'''
    if mode == '核验中':
        body = f'''<div class="stack">{status_card('认证核验中','认证结果正在确认，请稍候','success','clock')}{data_card}<div class="card" style="padding-top:4px;padding-bottom:4px">{row('刷新认证状态','查看最新核验结果','refresh')}</div><div class="notice info">{icon('clock','notice-icon')}<span>核验期间无需重复提交。</span></div></div>'''
    elif mode == '人工审核':
        body = f'''<div class="stack">{status_card('已进入人工审核','工作人员将核对认证资料，请耐心等待','warning','clock')}{data_card}<div class="card" style="padding-top:4px;padding-bottom:4px">{row('审核进度','当前无需额外操作','clock','warn|审核中')}</div><div class="notice warn">{icon('warning','notice-icon')}<span>审核结果更新后会在本页显示。</span></div></div>'''
    elif mode == '成功':
        body = f'''<div class="stack">{status_card('已完成实名认证','认证信息已通过核验，账号实名状态正常','success','shield')}<div class="card"><div class="info-grid"><div class="label">真实姓名</div><div class="value">李*</div><div class="label">身份证号</div><div class="value">440***********0098</div><div class="label">认证时间</div><div class="value">2026-07-20 09:18</div></div></div><div class="card" style="padding-top:4px;padding-bottom:4px">{row('认证状态','实名认证已通过','shield','success|已通过')}</div><p class="caption" style="text-align:center;margin:0">认证信息不可自行修改，如有疑问请联系客服</p></div>'''
    elif mode == '拒绝':
        body = f'''<div class="stack">{status_card('实名认证未通过','提交的信息未能通过核验，请根据提示处理','error','warning')}<div class="card"><h3 class="section-title">未通过原因</h3><p class="body" style="margin:0">身份信息与检测结果不一致，请确认信息后重新认证。</p></div><button class="primary-btn">重新认证</button><button class="secondary-btn">查看帮助</button></div>'''
    elif mode == '失败':
        body = f'''<div class="stack">{status_card('认证未完成','检测过程中出现异常，本次认证没有完成','error','warning')}<div class="card"><h3 class="section-title">下一步</h3>{row('检查网络与相机','确保网络稳定、相机可正常使用','camera')}{row('重新进行认证','原身份信息将重新确认','refresh')}</div><button class="primary-btn">重新认证</button></div>'''
    else:
        body = f'''<div class="stack">{status_card('认证流程已失效','本次认证已超时，请重新开始','warning','clock')}<div class="card"><h3 class="section-title">为什么会失效</h3><p class="body" style="margin:0">长时间未完成检测，或认证页面停留时间过长。</p></div><button class="primary-btn">重新开始认证</button><button class="secondary-btn">返回实名认证</button></div>'''
    return mobile_shell('实名认证', body)


def h5_callback(mode: str) -> str:
    mapping = {
        '加载': ('正在确认认证结果','请稍候，我们正在安全地确认本次认证结果。','clock','', '返回合伙云'),
        '处理中': ('认证结果处理中','结果仍在核验中，请稍后返回合伙云查看最新状态。','clock','warn','返回合伙云'),
        '成功': ('认证已完成','实名认证已通过，您可以返回合伙云继续使用。','shield','success','返回合伙云'),
        '人工审核': ('已进入人工审核','工作人员将进一步核对资料，结果更新后可在合伙云查看。','clock','warn','返回合伙云'),
        '未通过': ('认证未通过','本次认证未能通过核验，请返回合伙云查看原因并重新认证。','warning','error','返回合伙云'),
        '失效': ('认证页面已失效','该页面已过期或已被使用，请返回合伙云重新发起认证。','warning','error','返回合伙云'),
        '离线': ('网络连接不可用','请恢复网络后重试，或返回合伙云稍后查看认证状态。','offline','error','重新连接'),
    }
    title,desc,ico,tone,cta=mapping[mode]
    icon_cls='web-icon '+tone
    meta = '<div class="web-meta"><div><span>当前状态</span><b>'+('处理中' if mode in ['加载','处理中','人工审核'] else ('已完成' if mode=='成功' else '需要处理'))+'</b></div><div><span>下一步</span><b>'+('返回合伙云查看' if mode!='离线' else '恢复网络后重试')+'</b></div></div>'
    body=f'''<main class="web"><section class="web-head"><div class="brand"><div class="brand-mark">合</div><span>合伙云 Pro</span></div><h1>实名认证</h1><p>安全确认认证结果，并返回合伙云继续使用</p></section><section class="web-card"><div class="{icon_cls}">{SVG[ico]}</div><h2>{title}</h2><p>{desc}</p>{meta}<button class="primary-btn">{cta}</button><p class="caption" style="margin-top:14px">无需在此页面填写或提交任何身份信息</p></section></main>'''
    return web_shell(body)


def admin_list_content(mode: str) -> str:
    head='''<div class="admin-page-head"><div><h1>实名认证</h1><p>查看认证状态、核验结果和风险情况。</p></div><div class="admin-actions"><button class="admin-btn">刷新</button></div></div>'''
    metrics='''<div class="metrics"><div class="admin-card metric"><small>待处理</small><strong>18</strong><span>需要人工关注</span></div><div class="admin-card metric"><small>核验中</small><strong>42</strong><span>自动处理中</span></div><div class="admin-card metric"><small>已通过</small><strong>1,286</strong><span>近30天</span></div><div class="admin-card metric"><small>未通过</small><strong>37</strong><span>近30天</span></div></div>'''
    filters='''<div class="admin-card filter-card"><div class="filter-row"><input class="admin-input" placeholder="用户ID或手机号"/><select class="admin-select"><option>全部状态</option></select><select class="admin-select"><option>全部风险</option></select><div class="filter-spacer"></div><button class="admin-btn">重置</button><button class="admin-btn primary">查询</button></div></div>'''
    if mode == '加载':
        rows=''.join('<tr>'+''.join('<td><div class="skeleton" style="height:18px;width:'+str(w)+'%"></div></td>' for w in [72,58,55,68,62,64,48,40])+'</tr>' for _ in range(7))
        table=f'''<div class="admin-card table-card"><div class="table-toolbar"><strong>认证记录</strong><span>正在加载</span></div><table><thead><tr><th>用户</th><th>认证状态</th><th>认证服务</th><th>活体结果</th><th>失败原因</th><th>有效期</th><th>更新时间</th><th>操作</th></tr></thead><tbody>{rows}</tbody></table></div>'''
        return head+metrics+filters+table
    if mode == '正常列表':
        data=[('陈*','188****2318','待人工复核','warn','已配置','待复核','—','2026-07-20 09:26'),('周*','186****9072','核验中','','已配置','处理中','—','2026-07-20 09:18'),('林*','139****4421','已通过','success','已配置','已通过','—','2026-07-20 08:54'),('王*','137****1180','未通过','error','已配置','未通过','人脸与证件不一致','2026-07-20 08:31'),('赵*','158****3306','已过期','warn','已配置','未完成','认证流程已过期','2026-07-19 21:42'),('孙*','177****5520','认证失败','error','已配置','失败','活体检测未完成','2026-07-19 18:08')]
        rows=''
        for name,phone,status,tone,provider,live,fail,updated in data:
            rows+=f'''<tr><td><div class="user-cell"><div class="user-badge">{name[0]}</div><div><b>{name}</b><div style="font-size:12px;color:{C['text2']}">{phone}</div></div></div></td><td><span class="status-tag {tone}">{status}</span></td><td>{provider}</td><td>{live}</td><td>{fail}</td><td>2026-08-19</td><td>{updated}</td><td><span class="link">查看详情</span></td></tr>'''
        table=f'''<div class="admin-card table-card"><div class="table-toolbar"><strong>认证记录</strong><span>共 1,383 条</span></div><table><thead><tr><th>用户</th><th>认证状态</th><th>认证服务</th><th>活体结果</th><th>失败原因</th><th>有效期</th><th>更新时间</th><th>操作</th></tr></thead><tbody>{rows}</tbody></table><div class="pagination"><span class="page-btn">‹</span><span class="page-btn active">1</span><span class="page-btn">2</span><span class="page-btn">3</span><span class="page-btn">›</span></div></div>'''
        return head+metrics+filters+table
    if mode == '空列表':
        empty=f'''<div class="admin-card table-card"><div class="table-toolbar"><strong>认证记录</strong><span>0 条</span></div><div class="empty-admin"><div class="big-icon">{SVG['empty']}</div><h2>没有符合条件的认证记录</h2><p>可以调整状态、风险或关键词筛选后重新查询。</p><button class="admin-btn primary">清除筛选</button></div></div>'''
        return head+metrics+filters+empty
    if mode == '无权限':
        card=f'''<div class="admin-card"><div class="error-admin"><div class="big-icon" style="color:{C['warning']}">{SVG['lock']}</div><h2>无权查看实名认证数据</h2><p>当前账号没有实名认证查看权限，请联系系统管理员处理。</p><button class="admin-btn">返回数据总览</button></div></div>'''
        return head+card
    card=f'''<div class="admin-card"><div class="error-admin"><div class="big-icon">{SVG['warning']}</div><h2>认证列表暂时无法加载</h2><p>服务暂时不可用，请稍后重试。已有筛选条件不会丢失。</p><button class="admin-btn primary">重新加载</button></div></div>'''
    return head+metrics+filters+card


def admin_detail_content(mode: str) -> tuple[str,str]:
    head='''<div class="admin-page-head"><div><h1>实名认证详情</h1><p>查看认证结果，并按权限执行人工复核和敏感资料访问。</p></div><div class="admin-actions"><button class="admin-btn">返回列表</button><button class="admin-btn">刷新</button></div></div>'''
    if mode == '加载':
        sk='''<div class="detail-summary"><div class="admin-card summary-card"><div class="skeleton" style="height:58px"></div></div><div class="admin-card summary-card"><div class="skeleton" style="height:58px"></div></div><div class="admin-card summary-card"><div class="skeleton" style="height:58px"></div></div></div><div class="detail-grid"><div class="admin-card" style="height:580px"><div class="skeleton" style="height:46px;margin-bottom:18px"></div><div class="skeleton" style="height:160px;margin-bottom:18px"></div><div class="skeleton" style="height:280px"></div></div><div class="admin-card" style="height:420px"><div class="skeleton" style="height:28px;margin-bottom:18px"></div><div class="skeleton" style="height:90px;margin-bottom:18px"></div><div class="skeleton" style="height:44px"></div></div></div>'''
        return head+sk,''
    if mode == '无权限':
        card=f'''<div class="admin-card"><div class="error-admin"><div class="big-icon" style="color:{C['warning']}">{SVG['lock']}</div><h2>无权查看认证详情</h2><p>当前账号没有实名认证详情或敏感资料查看权限。</p><button class="admin-btn">返回认证列表</button></div></div>'''
        return head+card,''
    if mode == '404':
        card=f'''<div class="admin-card"><div class="empty-admin"><div class="big-icon">{SVG['empty']}</div><h2>认证记录不存在</h2><p>该记录可能已删除、失效或不在当前数据范围内。</p><button class="admin-btn primary">返回认证列表</button></div></div>'''
        return head+card,''
    conflict=''
    if mode == '乐观锁冲突':
        conflict='''<div class="conflict-banner"><div><strong>认证数据已经变化</strong><p>另一名管理员已更新该记录。当前写操作已暂停，请重新加载后核对最新状态。</p></div><button class="admin-btn">重新加载</button></div>'''
    summary='''<div class="detail-summary"><div class="admin-card summary-card"><div class="summary-user"><div class="avatar">认</div><div><strong>用户 U202607200018</strong><small>手机号 188****2318</small></div></div></div><div class="admin-card summary-card"><label>认证状态</label><span class="status-tag warn">待人工复核</span></div><div class="admin-card summary-card"><label>认证有效期</label><b>2026-08-19</b></div></div>'''
    tabs='''<div class="admin-card" style="padding:0"><div class="tabs"><div class="tab active">基本信息</div><div class="tab">活体与人证</div><div class="tab">敏感媒体</div><div class="tab">风险</div><div class="tab">复核记录</div><div class="tab">访问审计</div></div><div class="detail-section"><div class="section-head"><h2>身份与核验信息</h2><span class="status-tag warn">需要复核</span></div><div class="kv-grid"><div class="k">真实姓名</div><div class="v">陈*</div><div class="k">身份证号</div><div class="v">440***********2318</div><div class="k">活体结果</div><div class="v">已完成</div><div class="k">人证比对</div><div class="v">需要人工确认</div><div class="k">失败说明</div><div class="v">人脸与证件相似度需要复核</div><div class="k">提交时间</div><div class="v">2026-07-20 09:26</div></div><div class="evidence"><div class="evidence-box"><div class="evidence-face">{face}</div><b>证件资料</b><span>默认脱敏，仅授权后短时查看</span></div><div class="evidence-box"><div class="evidence-face">{camera}</div><b>活体采集结果</b><span>已完成采集，等待人工复核</span></div></div></div></div>'''.format(face=SVG['person'],camera=SVG['camera'])
    if mode == '人工复核':
        side=f'''<div class="admin-card side-card"><h2>人工复核</h2><p>请选择结论并填写原因。提交后将写入复核记录。</p><div class="admin-field"><label>复核结论</label><div class="choice-row"><span class="choice active">通过</span><span class="choice">不通过</span><span class="choice">升级复核</span></div></div><div class="admin-field"><label>复核原因</label><div class="admin-textarea">已核对身份信息与活体结果，资料一致。</div></div><div class="admin-field"><label>复核证据</label><div class="choice-row"><span class="choice active">身份信息</span><span class="choice active">活体结果</span></div></div><button class="admin-btn primary" style="width:100%">提交复核结论</button></div>'''
    elif mode == '乐观锁冲突':
        side=f'''<div class="admin-card side-card" style="opacity:.62"><h2>复核操作已暂停</h2><p>请重新加载最新数据后再继续。</p><button class="admin-btn" style="width:100%" disabled>人工复核</button><button class="admin-btn" style="width:100%;margin-top:10px" disabled>查看敏感资料</button></div>'''
    else:
        side=f'''<div class="admin-card side-card"><h2>复核操作</h2><p>敏感资料仅允许按明确用途短时查看，所有操作均会记录审计。</p><button class="admin-btn primary" style="width:100%;margin-bottom:10px">人工复核</button><button class="admin-btn" style="width:100%;margin-bottom:10px">查看敏感资料</button><button class="admin-btn danger" style="width:100%">申请冻结实名</button></div>'''
    content=head+conflict+summary+f'<div class="detail-grid"><div>{tabs}</div>{side}</div>'
    overlay=''
    if mode == '敏感资料授权':
        overlay=f'''<div class="admin-overlay"><div class="admin-dialog"><div style="width:52px;height:52px;border-radius:14px;background:{C['soft_blue']};color:{C['primary']};padding:13px;margin-bottom:14px">{SVG['lock']}</div><h3>查看敏感资料</h3><p>敏感资料仅用于当前复核，查看行为将记录到访问审计。</p><div class="dialog-form-grid"><div class="admin-field"><label>查看用途</label><div class="admin-textarea">人工复核身份信息与活体结果</div></div><div class="admin-field"><label>可查看时长</label><select class="admin-select" style="width:100%"><option>1 分钟</option></select></div></div><div class="notice warn" style="margin-top:8px">{icon('warning','notice-icon')}<span>到期后需要重新申请，禁止截图或导出。</span></div><div class="admin-dialog-actions"><button class="admin-btn">取消</button><button class="admin-btn primary">授权并查看</button></div></div></div>'''
    return content,overlay


SHOTS: list[Shot] = []

def add(page_id: str, state: str, kind: str, html: str, order: int, width: int, height: int, scale: int = 1, suffix: str=''):
    safe = re.sub(r'[^0-9A-Za-z\u4e00-\u9fff_-]+','-',state)
    filename = f'{page_id}_{order:02d}_{safe}{suffix}.png'
    SHOTS.append(Shot(page_id, order, state, filename, kind, html, width, height, scale))

for i,s in enumerate(['未认证','认证中','已认证'],1): add('SCR-ID-001',s,'mobile',mobile_identity_home(s),i,360,800,3)
for i,s in enumerate(['默认','字段错误','授权说明弹层','网络失败重试'],1): add('SCR-ID-002',s,'mobile',mobile_identity_form(s),i,360,800,3)
for i,s in enumerate(['准备','相机授权','加载','处理中','失败重试'],1): add('SCR-ID-003',s,'mobile',mobile_liveness(s),i,360,800,3)
for i,s in enumerate(['核验中','人工审核','成功','拒绝','失败','过期'],1): add('SCR-ID-004',s,'mobile',result_status(s),i,360,800,3)
for i,s in enumerate(['加载','处理中','成功','人工审核','未通过','失效','离线'],1): add('H5-012',s,'web',h5_callback(s),i,360,800,3)
for i,s in enumerate(['加载','正常列表','空列表','无权限','失败'],1): add('ADM-ID-001',s,'admin',admin_shell(admin_list_content(s)),i,1440,900,1)
# Responsive admin extra
add('ADM-ID-001','1200响应式','admin',admin_shell(admin_list_content('正常列表')),6,1200,900,1,'')
for i,s in enumerate(['加载','详情','敏感资料授权','人工复核','乐观锁冲突','无权限','404'],1):
    content,overlay=admin_detail_content(s)
    add('ADM-ID-002',s,'admin',admin_shell(content,overlay),i,1440,900,1)
content,overlay=admin_detail_content('详情')
add('ADM-ID-002','1200响应式','admin',admin_shell(content,overlay),8,1200,900,1)

async def render_all():
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True, executable_path='/usr/bin/chromium', args=['--no-sandbox','--font-render-hinting=none'])
        for shot in SHOTS:
            context = await browser.new_context(viewport={'width':shot.width,'height':shot.height}, device_scale_factor=shot.scale, locale='zh-CN')
            page = await context.new_page()
            await page.set_content(shot.html, wait_until='load')
            await page.screenshot(path=str(IMAGES/shot.filename), full_page=False)
            await context.close()
        await browser.close()

asyncio.run(render_all())

# Overview sheets.
FONT_REG = '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc'
FONT_BOLD = '/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc'
font_label = ImageFont.truetype(FONT_BOLD, 34)
font_small = ImageFont.truetype(FONT_REG, 26)
font_title = ImageFont.truetype(FONT_BOLD, 54)

page_titles = {
    'SCR-ID-001':'实名认证首页', 'SCR-ID-002':'实名信息输入', 'SCR-ID-003':'活体检测容器',
    'SCR-ID-004':'实名结果', 'H5-012':'活体回跳', 'ADM-ID-001':'管理端实名列表', 'ADM-ID-002':'管理端实名详情与复核'
}

def make_overview(page_id: str):
    shots=[s for s in SHOTS if s.page_id==page_id and '响应式' not in s.state]
    if shots[0].kind in ('mobile','web'):
        cols=min(4,len(shots)); rows=math.ceil(len(shots)/cols)
        cell_w=720; img_w=480; img_h=1067; pad=48; header=150; label_h=74
        canvas=Image.new('RGB',(pad*2+cols*cell_w,header+pad+rows*(img_h+label_h+pad)), '#F5F7FA')
        d=ImageDraw.Draw(canvas)
        d.text((pad,34),f'合伙云 Pro · R05 · {page_id} {page_titles[page_id]}',font=font_title,fill='#0B3B8E')
        d.text((canvas.width-650,50),'精确视觉规格 · 功能以开发文档为准',font=font_small,fill='#667085')
        for idx,s in enumerate(shots):
            im=Image.open(IMAGES/s.filename).convert('RGB')
            im.thumbnail((img_w,img_h),Image.Resampling.LANCZOS)
            x=pad+(idx%cols)*cell_w+(cell_w-im.width)//2
            y=header+pad+(idx//cols)*(img_h+label_h+pad)
            shadow=Image.new('RGBA',(im.width+30,im.height+30),(0,0,0,0)); sd=ImageDraw.Draw(shadow); sd.rounded_rectangle((15,15,im.width+14,im.height+14),radius=30,fill=(16,24,40,30))
            canvas.paste(shadow,(x-15,y-10),shadow)
            canvas.paste(im,(x,y))
            label=f'{idx+1:02d}  {s.state}'
            tw=d.textbbox((0,0),label,font=font_label)[2]
            d.rounded_rectangle((x+(im.width-tw)//2-18,y+img_h+18,x+(im.width+tw)//2+18,y+img_h+62),radius=14,fill='#1677FF')
            d.text((x+(im.width-tw)//2,y+img_h+23),label,font=font_label,fill='white')
    else:
        cols=2; rows=math.ceil(len(shots)/2); pad=48; header=150; img_w=1360; img_h=850; label_h=70
        canvas=Image.new('RGB',(pad*2+cols*img_w,header+pad+rows*(img_h+label_h+pad)), '#F5F7FA')
        d=ImageDraw.Draw(canvas)
        d.text((pad,34),f'合伙云 Pro · R05 · {page_id} {page_titles[page_id]}',font=font_title,fill='#0B3B8E')
        d.text((canvas.width-650,50),'管理端精确视觉规格 · 1440×900',font=font_small,fill='#667085')
        for idx,s in enumerate(shots):
            im=Image.open(IMAGES/s.filename).convert('RGB')
            im.thumbnail((img_w-40,img_h-20),Image.Resampling.LANCZOS)
            x=pad+(idx%2)*img_w+(img_w-im.width)//2
            y=header+pad+(idx//2)*(img_h+label_h+pad)
            canvas.paste(im,(x,y))
            label=f'{idx+1:02d}  {s.state}'
            d.rounded_rectangle((x,y+im.height+12,x+260,y+im.height+58),radius=12,fill='#1677FF')
            d.text((x+18,y+im.height+18),label,font=font_label,fill='white')
    out=IMAGES/f'{page_id}_OVERVIEW.png'
    canvas.save(out,optimize=True)
    return out

overviews={pid:make_overview(pid) for pid in page_titles}

# Master index preview.
thumbs=[]
for pid,path in overviews.items():
    im=Image.open(path).convert('RGB'); im.thumbnail((920,760),Image.Resampling.LANCZOS); thumbs.append((pid,im.copy()))
cols=2; rows=math.ceil(len(thumbs)/2); pad=52; cell_w=1020; cell_h=850; header=160
master=Image.new('RGB',(pad*2+cols*cell_w,header+rows*cell_h+pad),'#EEF5FF')
d=ImageDraw.Draw(master)
d.text((pad,38),'合伙云 Pro · R05 单一实名认证 · 精确视觉规格总览',font=font_title,fill='#0B3B8E')
d.text((pad,105),'Android / H5 / 管理后台 · 37 个业务状态 + 2 个响应式状态',font=font_small,fill='#667085')
for idx,(pid,im) in enumerate(thumbs):
    x=pad+(idx%2)*cell_w+(cell_w-im.width)//2; y=header+(idx//2)*cell_h+35
    master.paste(im,(x,y))
    d.text((x,y+im.height+12),f'{pid}  {page_titles[pid]}',font=font_label,fill='#182230')
master.save(IMAGES/'HHY_R05_IDENTITY_VISUAL_MASTER_OVERVIEW.png',optimize=True)

# Exact per-page specs.
common_mobile = f'''
- 画布：360dp × 800dp，导出 1080 × 2400px；状态栏 24dp；顶部导航内容高 56dp。
- 页面底色：`color.background.page` `{C['page']}`；表面：`color.background.surface`；主色：`color.brand.primary` `{C['primary']}`。
- 页面左右边距：`layout.pageHorizontalPaddingDp` 16dp；模块间距：`layout.sectionGapDp` 20dp；卡片间距 12dp；卡片内边距 16dp。
- 卡片圆角：`radiusDp.largeCard` 16dp；输入框/按钮圆角 12dp；标准卡片阴影 `shadow.card`。
- 顶部标题：`typographySp.pageTitle` 20/28/600；卡片标题 16/24/600；正文 14/22/400；说明 12/18/400；按钮 15/22/600。
- 主按钮高：`sizeDp.primaryButtonHeight` 48dp；输入框高 52dp；最小触控区 48dp。
- 动画：页面状态切换 `motionMs.standard` 200ms；按钮反馈 120ms；结果页强调 300ms；Snackbar 4000ms。
'''.strip()
common_admin = f'''
- 画布：1440 × 900px；响应式补图 1200 × 900px。
- 左侧导航宽：`adminWeb.sidebarWidthPx` 240px；窄屏折叠至 88px（本补充规格新增响应式实现值，见 Token 建议）。
- 顶栏：`adminWeb.headerHeightPx` 56px；页面边距 24px；区域间距 24px；卡片内边距 20px；圆角 12px。
- 表格行高：`adminWeb.tableRowHeightPx` 48px 为最小基线，本稿操作型列表使用 58px 内容行以容纳双行用户信息；需新增 Token 记录。
- 基础字号：14px；页面标题 20px；表头和辅助文字 12px；主按钮高 40px（需新增 Admin 操作按钮 Token）。
- 卡片边框 `color.border.default`；表面白色；页面底色 `color.background.page`；状态色使用 success/warning/error Token。
- 高敏资料默认脱敏；查看原图必须使用 16px 圆角、520px 宽受控弹窗，遮罩 `color.overlay.scrim`，并显示用途和时效。
'''.strip()

spec_data = {
    'SCR-ID-001': {
        'reference':'B12/P04', 'states':['未认证','认证中','已认证'],
        'structure':'标准顶栏 → 蓝色主状态卡 → 白色准备/进度/信息卡 → 主操作或状态入口 → 商业说明。禁止居中 Hero。',
        'filters':'不复制 B12/P04 已完成假状态、姓名、证件号、日期；未认证与认证中状态完全来自 SCR-ID-001。',
        'common':common_mobile,
    },
    'SCR-ID-002': {
        'reference':'B12/P04 视觉语言 + 批准补充规格', 'states':['默认','字段错误','授权说明弹层','网络失败重试'],
        'structure':'标准顶栏 → 标题说明 → 商业错误提示（按需） → 白色表单卡 → 授权勾选 → 重载/提交按钮。',
        'filters':'不展示协议版本ID、幂等键、接口名、错误码、请求号；协议加载失败保留表单输入。',
        'common':common_mobile,
    },
    'SCR-ID-003': {
        'reference':'B12/P04 顶栏与卡片语言 + 批准补充规格', 'states':['准备','相机授权','加载','处理中','失败重试'],
        'structure':'标准顶栏 → 步骤标签 → 240dp 活体取景区 → 状态提示/检测前说明 → 主操作。',
        'filters':'不展示供应商名称、AppKey、Token、活体URL、轮询次数和技术错误；相机说明只描述用户下一步。',
        'common':common_mobile,
    },
    'SCR-ID-004': {
        'reference':'B12/P04', 'states':['核验中','人工审核','成功','拒绝','失败','过期'],
        'structure':'完全沿用 P04：标准顶栏 → 主状态卡 → 身份信息卡（可用时） → 后续动作列表/主按钮 → 辅助说明。',
        'filters':'不复制示例姓名、证件号、时间和已通过假状态；状态、原因和动作由服务端事实决定。',
        'common':common_mobile,
    },
    'H5-012': {
        'reference':'h5Tech 渐变 Token + 批准补充规格', 'states':['加载','处理中','成功','人工审核','未通过','失效','离线'],
        'structure':'深蓝品牌头部 → 白色居中状态卡 → 状态图标 → 主结论 → 最小业务说明 → 状态摘要 → 返回/重试按钮。',
        'filters':'地址栏 state 在加载后立即清理；页面不显示 state、会话ID、供应商、接口、错误码或请求编号。',
        'common':common_mobile.replace('顶部导航内容高 56dp。','H5 不显示 App 顶栏；品牌头部约 188dp，主体卡片左右 16dp。'),
    },
    'ADM-ID-001': {
        'reference':'ADM-LIST 标准模板 + 批准补充规格', 'states':['加载','正常列表','空列表','无权限','失败','1200响应式'],
        'structure':'侧栏/顶栏 → 页面标题 → 四项状态指标 → 筛选卡 → 实名列表表格 → 分页；无权限与错误只替换内容区。',
        'filters':'用户、手机号和证件均为脱敏或合成数据；不显示供应商技术代码，不新增批量审核、同步导出或直接冻结。',
        'common':common_admin,
    },
    'ADM-ID-002': {
        'reference':'ADM-REVIEW 标准模板 + 批准补充规格', 'states':['加载','详情','敏感资料授权','人工复核','乐观锁冲突','无权限','404','1200响应式'],
        'structure':'页面标题 → 三张摘要卡 → 冲突提示（按需） → 左侧六标签详情与证据 → 右侧受控复核操作；窄屏下操作区下移。',
        'filters':'姓名、证件、手机默认脱敏；短时原图需用途和时效；不显示 expectedVersion、幂等键、权限代码和内部错误。',
        'common':common_admin,
    },
}

# Token additions proposal kept explicit rather than silently using framework defaults.
token_additions = {
    'meta': {'project':'合伙云 Pro','scope':'R05 identity visual supplement','status':'PROPOSED_FOR_MERGE'},
    'identity': {
        'livenessFrameSizeDp': {'value':240,'reason':'活体检测容器需要稳定的圆角取景区域，现有 Token 无等价尺寸。'},
        'livenessPanelRadiusDp': {'value':20,'reason':'取景区比标准卡片更强调，需与 dialog 20dp 语言一致。'},
        'h5CallbackCardRadiusDp': {'value':20,'reason':'H5 状态回跳卡与认证安全视觉保持一致，现有移动卡片仅 16dp。'},
    },
    'adminWeb': {
        'collapsedSidebarWidthPx': {'value':88,'reason':'1200px 窄桌面下保持内容区可用，现有 Token 仅定义 240px 展开态。'},
        'operationButtonHeightPx': {'value':40,'reason':'后台筛选与页面操作按钮需要统一高度，现有 Token 未登记。'},
        'identityTableContentRowHeightPx': {'value':58,'reason':'实名列表用户列包含姓名与脱敏手机号双行信息，高于 48px 最小基线。'},
        'sensitiveAccessDialogWidthPx': {'value':520,'reason':'需同时容纳用途说明与时效选择，并满足高敏操作信息密度。'},
    }
}
(FROZEN/'TOKEN_ADDITIONS_PROPOSAL.json').write_text(json.dumps(token_additions,ensure_ascii=False,indent=2),'utf-8')

for pid,meta in spec_data.items():
    state_lines=[]
    for s in [x for x in SHOTS if x.page_id==pid]:
        state_lines.append(f'| {s.order:02d} | {s.state} | `images/{s.filename}` | {"1080×2400" if s.kind in ("mobile","web") else f"{s.width}×{s.height}"} |')
    md=f'''# {pid} · {page_titles[pid]} · R05 精确视觉规格

状态：`FROZEN_VISUAL_SPEC`  
参考：`{meta['reference']}`  
Token：`hhy_design_tokens_v1.2.2.json` + `TOKEN_ADDITIONS_PROPOSAL.json`

## 1. 页面建模

{meta['structure']}

## 2. 业务过滤

{meta['filters']}

## 3. 通用视觉参数

{meta['common']}

## 4. 状态图片

| 序号 | 状态 | 图片 | 尺寸 |
| --- | --- | --- | --- |
{chr(10).join(state_lines)}

总览：`images/{pid}_OVERVIEW.png`

## 5. 状态差异规则

'''
    for st in meta['states']:
        md+=f'- **{st}**：仅改变该状态需要的状态卡、提示、按钮与可用性；其余区域顺序、边距、字体、卡片和导航保持不变。\n'
    md+='''
## 6. 验收要求

- 页面区域顺序、主次层级、卡片和按钮位置必须按本稿还原。
- 原始 dp/sp/hex 不得直接写入页面代码；应合并 Token 后引用。
- 正式 UI 不得出现 requestId、traceId、state、sessionId、供应商代码、AppKey、Token、接口名或异常栈。
- 敏感字段严格按页面规格脱敏，管理端原图必须经过用途授权和访问审计。
- 实现后提供同尺寸真机/浏览器截图并进入 `ui_visual_acceptance.csv` 比对；未通过不得关闭 R05。
'''
    (SPECS/f'{pid}.md').write_text(md,'utf-8')

# Update visual acceptance and page specs in overlay.
def read_csv(path: Path):
    with path.open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
def write_csv(path: Path, rows):
    path.parent.mkdir(parents=True,exist_ok=True)
    with path.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=list(rows[0].keys())); w.writeheader(); w.writerows(rows)

visual_rows=read_csv(SRC/'catalogs/ui_visual_acceptance.csv')
for r in visual_rows:
    pid=r['页面ID']
    if pid in spec_data:
        files=';'.join('R05-UI-FROZEN/images/'+s.filename for s in SHOTS if s.page_id==pid)
        r['视觉来源']=f'R05-UI-FROZEN/specs/{pid}.md'
        r['覆盖状态']='EXACT_SUPPLEMENTAL'
        r['参考证据']=files+f';R05-UI-FROZEN/images/{pid}_OVERVIEW.png'
        r['验收状态']='VISUAL_SPEC_FROZEN'
        r['说明']='精确视觉规格和全状态效果图已冻结；等待实现截图后执行视觉回归并转PASS。'
write_csv(CATALOGS/'ui_visual_acceptance.csv',visual_rows)

page_rows=read_csv(SRC/'catalogs/ui_page_specifications.csv')
for r in page_rows:
    if r['页面ID'] in spec_data:
        r['UI参考']=f'R05-UI-FROZEN/specs/{r["页面ID"]}.md;R05-UI-FROZEN/images/{r["页面ID"]}_OVERVIEW.png'
write_csv(CATALOGS/'ui_page_specifications.csv',page_rows)

# Copy the seven page specs with only the UI reference line updated.
for platform in ['android','h5','admin']:
    src_dir=SRC/'page-specs'/platform
    for p in src_dir.glob('*.md'):
        text=p.read_text('utf-8')
        pid=p.name.split('_',1)[0]
        if pid in spec_data:
            text=re.sub(r'- UI参考：`[^`]*`',f'- UI参考：`R05-UI-FROZEN/specs/{pid}.md;R05-UI-FROZEN/images/{pid}_OVERVIEW.png`',text)
        target=PAGE_SPECS/platform/p.name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_text(text,'utf-8')

# Visual manifest.
def sha256(path: Path) -> str:
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()

manifest={
    'project':'合伙云 Pro','release':'R05','package':'R05 UI精确视觉规格补充','status':'FROZEN_VISUAL_SPEC',
    'generated_at':'2026-07-20','source_package_sha256':sha256(Path('/mnt/data/合伙云Pro_R05_UI精确视觉规格补充包_20260720_092405.zip')),
    'token_file':'REFERENCE_ONLY/tokens/hhy_design_tokens_v1.2.2.json','token_sha256':sha256(TOKENS_PATH),
    'reference':{'file':'REFERENCE_ONLY/reference/B12/HHY_B12_8PAGE_UI_REFERENCE.png','panel':'B12/P04','sha256':sha256(SRC/'reference/B12/HHY_B12_8PAGE_UI_REFERENCE.png')},
    'pages':[],'business_filters':[
        '不包含真实姓名、真实身份证号、真实手机号、账号、密钥或供应商秘密',
        '不展示 requestId、traceId、state、会话ID、Token、AppKey、接口名或异常栈',
        '用户页面只展示业务状态和下一步；管理端敏感信息默认脱敏并保留授权/审计结构',
        '效果图不新增未登记业务；功能字段动作仍以页面规格和合同为准'
    ]
}
for pid,meta in spec_data.items():
    images=[]
    for s in SHOTS:
        if s.page_id==pid:
            p=IMAGES/s.filename
            images.append({'state':s.state,'file':f'images/{s.filename}','sha256':sha256(p),'size':list(Image.open(p).size)})
    ov=IMAGES/f'{pid}_OVERVIEW.png'
    manifest['pages'].append({'page_id':pid,'name':page_titles[pid],'spec':f'specs/{pid}.md','spec_sha256':sha256(SPECS/f'{pid}.md'),'reference':meta['reference'],'images':images,'overview':{'file':f'images/{pid}_OVERVIEW.png','sha256':sha256(ov),'size':list(Image.open(ov).size)}})
(FROZEN/'VISUAL_MANIFEST.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),'utf-8')

freeze=f'''# R05 单一实名认证 UI 精确视觉规格冻结说明

## 结论

本包为 R05 七个实名认证页面/工作台补齐了可直接施工、可量化验收的精确效果图和视觉参数。视觉状态为 `FROZEN_VISUAL_SPEC`，不改变 API、数据库、业务状态机或 Release 范围。

## 交付规模

- 页面组：7
- 业务状态效果图：37
- 管理端响应式效果图：2
- 页面总览图：7
- 全局总览图：1
- 精确规格文件：7
- Token 新增建议：7 项，全部显式列入 `TOKEN_ADDITIONS_PROPOSAL.json`

## 核心裁决

1. Android 实名首页与结果页继承 `B12/P04` 的顶栏、主状态卡、信息卡和动作列表，不允许继续使用当前实现的居中 Hero。
2. 实名信息输入、活体容器、H5 回跳和管理端页面使用本补充规格，不再允许 `TOKENS_ONLY` 或粗粒度面板范围进入施工。
3. 第三方实名认证 AppKey 未配置只影响供应商调用，不影响页面结构、授权说明和本地表单的设计与实现。
4. 用户可见 UI 不显示任何技术字段；管理端敏感资料默认脱敏，短时查看需要用途、时效和审计。
5. `ui_visual_acceptance.csv` 已更新为 `VISUAL_SPEC_FROZEN`；实现后仍需提供真机/浏览器截图并通过视觉回归，才能转为 `PASS`。

## 施工入口

- 总览：`images/HHY_R05_IDENTITY_VISUAL_MASTER_OVERVIEW.png`
- 页面规格：`specs/`
- 图片：`images/`
- 视觉 Manifest：`VISUAL_MANIFEST.json`
- Token 建议：`TOKEN_ADDITIONS_PROPOSAL.json`
'''
(FROZEN/'冻结说明.md').write_text(freeze,'utf-8')

summary=f'''# ChatGPT Web 对 Codex R05 UI 补充包的完善摘要

## 已完成

- 按任务说明完成 7 组页面、37 个业务状态和 2 个管理端响应式状态效果图。
- Android 页面以 B12/P04 与现有 Design Token 为视觉母版，移除居中 Hero 和框架默认风格。
- 新增 7 份逐页视觉规格、7 张状态总览和 1 张全局总览。
- 显式提出 7 项必要 Token，不静默使用框架默认值。
- 更新 `ui_visual_acceptance.csv` 和 `ui_page_specifications.csv` 覆盖项，但未篡改业务合同、API、数据库和源代码。
- 原 Codex 补充包全部保存在 `REFERENCE_ONLY/`，新内容在 `EDITABLE_OVERLAY/`。

## 应用方式

Codex 将 `EDITABLE_OVERLAY/R05-UI-FROZEN/` 合并到项目设计目录，并将 `EDITABLE_OVERLAY/catalogs/` 与 `EDITABLE_OVERLAY/page-specs/` 中对应文件覆盖回权威目录。随后按逐页规格重构 UI，提交同尺寸实现截图，再将视觉验收状态从 `VISUAL_SPEC_FROZEN` 更新为 `PASS`。
'''
(OVR/'CHATGPT_WEB_CHANGE_SUMMARY.md').write_text(summary,'utf-8')

# Return manifest for entire output.
all_files=[]
for p in sorted(OUT_ROOT.rglob('*')):
    if p.is_file():
        all_files.append({'path':p.relative_to(OUT_ROOT).as_posix(),'sha256':sha256(p),'size':p.stat().st_size})
return_manifest={'package':'合伙云Pro_R05_UI精确视觉规格补充包_20260720_已完善冻结版','created_at':'2026-07-20','files':all_files,'counts':{'files':len(all_files),'png':sum(1 for x in all_files if x['path'].endswith('.png')),'specs':7,'business_state_images':37,'responsive_images':2,'overview_images':8}}
(OVR/'RETURN_MANIFEST.json').write_text(json.dumps(return_manifest,ensure_ascii=False,indent=2),'utf-8')

# Copy generator itself for reproducibility.
shutil.copy2(Path(__file__),GEN/'generate_r05_ui.py')

print(json.dumps({'output':str(OUT_ROOT),'shots':len(SHOTS),'overviews':len(overviews)+1,'files':sum(1 for p in OUT_ROOT.rglob('*') if p.is_file())},ensure_ascii=False,indent=2))
