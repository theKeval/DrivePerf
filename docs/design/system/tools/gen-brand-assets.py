#!/usr/bin/env python3
"""Generates the DrivePerf brand SVGs in docs/design/system/assets/.
usage: python3 docs/design/system/tools/gen-brand-assets.py
Geometry of "The Loop" (recommended mark): a 270° trail arc that fades in clockwise,
ending in a round head, followed by the G-dot floating in the 90° gap ahead of it."""
import math, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'assets')
os.makedirs(OUT, exist_ok=True)

MINT, MINT_HI, MINT_DEEP = '#5BE7C4', '#C6F8EA', '#12B893'
NIGHT = '#080B10'
FONT = "'Google Sans Flex', 'Roboto Flex', -apple-system, 'SF Pro Display', system-ui, sans-serif"

def pt(cx, cy, r, a):
    t = math.radians(a)
    return cx + r * math.cos(t), cy + r * math.sin(t)

def arc(cx, cy, r, a0, a1):
    (x0, y0), (x1, y1) = pt(cx, cy, r, a0), pt(cx, cy, r, a1)
    large = 1 if (a1 - a0) % 360 > 180 else 0
    return f'M{x0:.2f} {y0:.2f} A{r:.2f} {r:.2f} 0 {large} 1 {x1:.2f} {y1:.2f}'

# Loop geometry in unit terms (scaled by size)
HEAD, SPAN, DOT_AHEAD = -78.0, 270.0, 34.0   # trail head angle, trail span, dot angle ahead of head

def loop(cx, cy, r, w, color=MINT, head_color=None, dot_color='#FFFFFF', fade=True, segs=40, dot_scale=1.22, glow=False, opacity=1.0, dot_stroke=None):
    """Return SVG elements for the Loop mark."""
    tail = HEAD - SPAN
    out = []
    if glow:
        out.append(f'<g filter="url(#glow)">')
    if fade:
        # 6 butt-joined sub-arcs, each with a userSpace linear gradient along its chord (monotonic for <=90°),
        # approximating an angular fade without visible seams. The head gets a round cap at full opacity.
        n = 6
        ramp = lambda f: 0.03 + 0.97 * (f ** 1.5)
        gid = f'tr{abs(hash((cx, cy, r, w, color))) % 10**6}'
        defs = []
        for i in range(n):
            a0, a1 = tail + SPAN * i / n, tail + SPAN * (i + 1) / n
            (x0, y0), (x1, y1) = pt(cx, cy, r, a0), pt(cx, cy, r, a1)
            defs.append(f'<linearGradient id="{gid}{i}" gradientUnits="userSpaceOnUse" x1="{x0:.2f}" y1="{y0:.2f}" x2="{x1:.2f}" y2="{y1:.2f}">'
                        f'<stop offset="0" stop-color="{color}" stop-opacity="{ramp(i/n)*opacity:.3f}"/>'
                        f'<stop offset="1" stop-color="{color}" stop-opacity="{ramp((i+1)/n)*opacity:.3f}"/></linearGradient>')
            out.append(f'<path d="{arc(cx, cy, r, a0, a1 + (0.25 if i < n-1 else 0))}" fill="none" stroke="url(#{gid}{i})" stroke-width="{w:.2f}" stroke-linecap="butt"/>')
        hx, hy = pt(cx, cy, r, HEAD)
        out.append(f'<circle cx="{hx:.2f}" cy="{hy:.2f}" r="{w/2:.2f}" fill="{color}" opacity="{opacity}"/>')
        out.insert(1 if glow else 0, '<defs>' + ''.join(defs) + '</defs>')
    else:
        out.append(f'<path d="{arc(cx, cy, r, tail, HEAD)}" fill="none" stroke="{color}" stroke-width="{w:.2f}" stroke-linecap="round" opacity="{opacity}"/>')
    dx, dy = pt(cx, cy, r, HEAD + DOT_AHEAD)
    ds = f' stroke="{dot_stroke}" stroke-width="{w*0.12:.2f}"' if dot_stroke else ''
    out.append(f'<circle cx="{dx:.2f}" cy="{dy:.2f}" r="{w*dot_scale/2*1.0:.2f}" fill="{dot_color}"{ds} opacity="{opacity}"/>')
    if glow:
        out.append('</g>')
    return '\n  '.join(out)

def glow_filter(std=6, color=MINT, op=0.55):
    return (f'<filter id="glow" x="-30%" y="-30%" width="160%" height="160%">'
            f'<feGaussianBlur in="SourceGraphic" stdDeviation="{std}" result="b"/>'
            f'<feFlood flood-color="{color}" flood-opacity="{op}"/><feComposite in2="b" operator="in" result="g"/>'
            f'<feMerge><feMergeNode in="g"/><feMergeNode in="SourceGraphic"/></feMerge></filter>')

def svg(w, h, body, defs='', title=''):
    t = f'<title>{title}</title>' if title else ''
    d = f'<defs>{defs}</defs>' if defs else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">{t}{d}\n  {body}\n</svg>\n'

def write(name, content):
    with open(os.path.join(OUT, name), 'w') as f:
        f.write(content)
    print('wrote', name)

# ---------------- Logo concepts ----------------
# A. The Loop (recommended)
write('logo-a-loop.svg', svg(240, 240,
    f'<rect width="240" height="240" rx="48" fill="{NIGHT}"/>\n  ' + loop(120, 120, 66, 22, glow=True),
    defs=glow_filter(5), title='DrivePerf logo concept A: The Loop (recommended)'))
write('logo-a-loop-mono.svg', svg(120, 120, loop(60, 60, 40, 13, color='#0B0E13', dot_color='#0B0E13', fade=False),
    title='The Loop, single colour (small sizes, notification icon, watermark)'))
write('logo-a-loop-on-light.svg', svg(120, 120, loop(60, 60, 40, 13, color='#079777', dot_color='#0B0E13'),
    title='The Loop on light backgrounds'))

# B. The Line: a smooth racing line through one corner, apex dot on the inside
line_path = 'M46 196 C46 150 70 128 104 116 C140 104 166 86 182 44'
write('logo-b-line.svg', svg(240, 240,
    f'<rect width="240" height="240" rx="48" fill="{NIGHT}"/>\n'
    f'  <path d="M58 200 C58 154 80 134 112 122 C146 110 170 92 186 50" fill="none" stroke="#1E2733" stroke-width="44" stroke-linecap="round"/>\n'
    f'  <path d="{line_path}" fill="none" stroke="url(#lg)" stroke-width="18" stroke-linecap="round"/>\n'
    f'  <circle cx="128" cy="96" r="12" fill="#FFFFFF"/>',
    defs=f'<linearGradient id="lg" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="{MINT}" stop-opacity="0.15"/><stop offset="0.6" stop-color="{MINT}"/><stop offset="1" stop-color="{MINT_HI}"/></linearGradient>',
    title='DrivePerf logo concept B: The Line'))

# C. The Trace: a D whose stem is a raw signal that resolves into a smooth bowl
pts = []
for i in range(0, 25):
    y = 196 - i * 6
    amp = 16 * (1 - i / 24) ** 1.5
    x = 64 + (amp if i % 2 else -amp)
    pts.append(f'{x:.1f} {y:.1f}')
trace = 'M' + ' L'.join(pts) + ' L64 52 L112 52 C162 52 190 84 190 124 C190 164 162 196 112 196 L96 196'
write('logo-c-trace.svg', svg(240, 240,
    f'<rect width="240" height="240" rx="48" fill="{NIGHT}"/>\n'
    f'  <path d="{trace}" fill="none" stroke="url(#tg)" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/>\n'
    f'  <circle cx="84" cy="196" r="10" fill="#FFFFFF"/>',
    defs=f'<linearGradient id="tg" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#A3AAB6"/><stop offset="0.45" stop-color="{MINT}"/><stop offset="1" stop-color="{MINT_HI}"/></linearGradient>',
    title='DrivePerf logo concept C: The Trace'))

# Wordmark + lockups (text set in the system face; outline before production)
def wordmark(x, y, size, fill, perf_fill=None):
    pf = perf_fill or fill
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="650" '
            f'letter-spacing="{size*0.01:.2f}" style="font-stretch:112%" fill="{fill}">Drive<tspan fill="{pf}">Perf</tspan></text>')
write('wordmark.svg', svg(520, 120, wordmark(10, 84, 72, '#F2F4F7'), title='DrivePerf wordmark (dark backgrounds)'))
write('wordmark-on-light.svg', svg(520, 120, wordmark(10, 84, 72, '#0B0E13'), title='DrivePerf wordmark (light backgrounds)'))
write('lockup-horizontal.svg', svg(660, 160,
    f'<rect width="660" height="160" rx="32" fill="{NIGHT}"/>\n  ' + loop(80, 80, 44, 15, glow=True) + '\n  ' + wordmark(150, 104, 66, '#F2F4F7') +
    f'\n  <text x="153" y="134" font-family="{FONT}" font-size="17" font-weight="500" letter-spacing="0.4" fill="#A3AAB6">Smooth is a skill.</text>',
    defs=glow_filter(4), title='DrivePerf horizontal lockup with tagline'))
write('lockup-stacked.svg', svg(360, 360,
    f'<rect width="360" height="360" rx="56" fill="{NIGHT}"/>\n  ' + loop(180, 140, 70, 23, glow=True) + '\n  ' +
    f'<text x="180" y="290" text-anchor="middle" font-family="{FONT}" font-size="48" font-weight="650" style="font-stretch:112%" fill="#F2F4F7">DrivePerf</text>\n'
    f'  <text x="180" y="324" text-anchor="middle" font-family="{FONT}" font-size="16" font-weight="500" fill="#A3AAB6">Every drive, decoded.</text>',
    defs=glow_filter(5), title='DrivePerf stacked lockup'))

# ---------------- iOS app icon (1024, square; the system applies the mask) ----------------
BG_DEF = ('<radialGradient id="bg" cx="0.3" cy="0.15" r="1.15"><stop offset="0" stop-color="#17344C"/>'
          '<stop offset="0.45" stop-color="#0A1420"/><stop offset="1" stop-color="#05070A"/></radialGradient>'
          '<radialGradient id="halo" cx="0.5" cy="0.5" r="0.5"><stop offset="0.55" stop-color="#5BE7C4" stop-opacity="0.16"/><stop offset="1" stop-color="#5BE7C4" stop-opacity="0"/></radialGradient>')
C, R, W = 512, 300, 104
def ios_icon(name, bg, mark, defs, title):
    write(name, svg(1024, 1024, bg + '\n  ' + mark, defs=defs, title=title))

ios_icon('icon-ios-default.svg', '<rect width="1024" height="1024" fill="url(#bg)"/><circle cx="512" cy="512" r="430" fill="url(#halo)"/>',
         loop(C, C, R, W, glow=True), BG_DEF + glow_filter(22, MINT, 0.6), 'iOS app icon, Default appearance')
ios_icon('icon-ios-dark.svg', '<rect width="1024" height="1024" fill="#000000"/><circle cx="512" cy="512" r="430" fill="url(#halo)"/>',
         loop(C, C, R, W, color='#6FF2D0', glow=True), BG_DEF + glow_filter(26, MINT, 0.75), 'iOS app icon, Dark appearance')
ios_icon('icon-ios-tinted.svg', '<rect width="1024" height="1024" fill="#000000"/>',
         loop(C, C, R, W, color='#FFFFFF', dot_color='#FFFFFF'), '', 'iOS app icon, Tinted appearance (greyscale source; the system applies the tint)')
ios_icon('icon-ios-clear.svg',
         '<rect width="1024" height="1024" fill="url(#wall)"/><rect width="1024" height="1024" fill="#FFFFFF" opacity="0.10"/>',
         loop(C, C, R, W, color='#FFFFFF', dot_color='#FFFFFF', opacity=0.92, dot_stroke='#FFFFFF') +
         f'\n  <path d="{arc(C, C, R + W*0.32, -200, -110)}" fill="none" stroke="#FFFFFF" stroke-width="6" stroke-linecap="round" opacity="0.55"/>',
         '<linearGradient id="wall" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6A86A8"/><stop offset="1" stop-color="#2B3A52"/></linearGradient>',
         'iOS app icon, Clear appearance (preview over a sample wallpaper; glass is applied by the system)')
# Icon Composer layers (back to front)
write('icon-ios-layer-0-background.svg', svg(1024, 1024, '<rect width="1024" height="1024" fill="url(#bg)"/><circle cx="512" cy="512" r="430" fill="url(#halo)"/>', defs=BG_DEF, title='Icon Composer layer 0: background (night sky)'))
tail = HEAD - SPAN
write('icon-ios-layer-1-trail.svg', svg(1024, 1024, loop(C, C, R, W).split('<circle')[0].rstrip(), title='Icon Composer layer 1: trail (glass: on, specular: on, shadow: neutral)'))
dx, dy = pt(C, C, R, HEAD + DOT_AHEAD)
write('icon-ios-layer-2-dot.svg', svg(1024, 1024, f'<circle cx="{dx:.1f}" cy="{dy:.1f}" r="{W*1.22/2:.1f}" fill="#FFFFFF"/>', title='Icon Composer layer 2: G-dot (glass: on, translucency 0, highest layer for parallax)'))

# ---------------- Android adaptive icon (108 dp canvas, 66 dp safe zone) ----------------
AC, AR, AW = 54, 22.5, 8
write('icon-android-background.svg', svg(108, 108, '<rect width="108" height="108" fill="url(#bg)"/>', defs=BG_DEF.replace('#5BE7C4', '#5BE7C4'), title='Android adaptive icon: background layer (108 dp)'))
write('icon-android-foreground.svg', svg(108, 108, loop(AC, AC, AR, AW, segs=28), title='Android adaptive icon: foreground layer (108 dp, mark inside the 66 dp safe zone)'))
write('icon-android-monochrome.svg', svg(108, 108, loop(AC, AC, AR, AW, color='#000000', dot_color='#000000', fade=False), title='Android themed icon: monochrome layer (system tints it)'))

def adaptive_preview():
    tiles = []
    shapes = [('circle', 'circle'), ('squircle', 'squircle'), ('rounded', 'rounded square')]
    x = 24
    for theme in ['brand', 'themed-light', 'themed-dark']:
        for sid, label in shapes:
            clip = {'circle': f'<circle cx="{x+54}" cy="78" r="54"/>',
                    'squircle': f'<path d="M{x+54} 24 C{x+100} 24 {x+108} 32 {x+108} 78 C{x+108} 124 {x+100} 132 {x+54} 132 C{x+8} 132 {x} 124 {x} 78 C{x} 32 {x+8} 24 {x+54} 24Z"/>',
                    'rounded': f'<rect x="{x}" y="24" width="108" height="108" rx="26"/>'}[sid]
            cid = f'c{theme}{sid}'
            if theme == 'brand':
                inner = f'<rect x="{x-27}" y="-3" width="162" height="162" fill="url(#bg)"/>' + loop(x + 54, 78, AR * 1.5, AW * 1.5, segs=28)
            elif theme == 'themed-light':
                inner = f'<rect x="{x-27}" y="-3" width="162" height="162" fill="#D7E8E1"/>' + loop(x + 54, 78, AR * 1.5, AW * 1.5, color='#1F4D40', dot_color='#1F4D40', fade=False)
            else:
                inner = f'<rect x="{x-27}" y="-3" width="162" height="162" fill="#1F2B28"/>' + loop(x + 54, 78, AR * 1.5, AW * 1.5, color='#A8DCCB', dot_color='#A8DCCB', fade=False)
            tiles.append(f'<clipPath id="{cid}">{clip}</clipPath><g clip-path="url(#{cid})">{inner}</g>')
            x += 132
        x += 24
    labels = (f'<text x="24" y="166" font-family="{FONT}" font-size="13" fill="#A3AAB6">Brand (circle / squircle / rounded)</text>'
              f'<text x="444" y="166" font-family="{FONT}" font-size="13" fill="#A3AAB6">Themed, light wallpaper</text>'
              f'<text x="864" y="166" font-family="{FONT}" font-size="13" fill="#A3AAB6">Themed, dark wallpaper</text>')
    return svg(1284, 184, f'<rect width="1284" height="184" fill="#10151C"/>' + ''.join(tiles) + labels, defs=BG_DEF, title='Android adaptive + themed icon previews under OEM masks')
write('icon-android-adaptive-preview.svg', adaptive_preview())

def ios_preview():
    names = [('icon-ios-default', 'Default'), ('icon-ios-dark', 'Dark'), ('icon-ios-tinted', 'Tinted (mint tint)'), ('icon-ios-clear', 'Clear')]
    parts, x = [], 24
    for i, (_, label) in enumerate(names):
        cid = f'm{i}'
        parts.append(f'<clipPath id="{cid}"><rect x="{x}" y="24" width="180" height="180" rx="41"/></clipPath>')
        if i == 0:
            inner = f'<rect x="{x}" y="24" width="180" height="180" fill="url(#bg)"/>' + loop(x + 90, 114, 52.7, 18.3, glow=False)
        elif i == 1:
            inner = f'<rect x="{x}" y="24" width="180" height="180" fill="#000"/>' + loop(x + 90, 114, 52.7, 18.3, color='#6FF2D0')
        elif i == 2:
            inner = f'<rect x="{x}" y="24" width="180" height="180" fill="#0E1F1A"/>' + loop(x + 90, 114, 52.7, 18.3, color='#8FEBD2', dot_color='#C6F8EA')
        else:
            inner = (f'<rect x="{x}" y="24" width="180" height="180" fill="url(#wall)"/><rect x="{x}" y="24" width="180" height="180" fill="#fff" opacity="0.14"/>'
                     + loop(x + 90, 114, 52.7, 18.3, color='#FFFFFF', dot_color='#FFFFFF', opacity=0.9))
        parts.append(f'<g clip-path="url(#{cid})">{inner}</g><rect x="{x+0.5}" y="24.5" width="179" height="179" rx="41" fill="none" stroke="#FFFFFF" stroke-opacity="0.18"/>')
        parts.append(f'<text x="{x+90}" y="232" text-anchor="middle" font-family="{FONT}" font-size="14" fill="#A3AAB6">{label}</text>')
        x += 212
    return svg(872, 252, '<rect width="872" height="252" fill="#10151C"/>' + ''.join(parts),
               defs=BG_DEF + '<linearGradient id="wall" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6A86A8"/><stop offset="1" stop-color="#2B3A52"/></linearGradient>',
               title='iOS 26 icon appearances preview')
write('icon-ios-appearances-preview.svg', ios_preview())

# Notification small icon (Android status bar, 24 dp, white on transparent)
write('icon-android-notification.svg', svg(24, 24, loop(12, 12, 7.2, 2.6, color='#FFFFFF', dot_color='#FFFFFF', fade=False), title='Android status-bar notification icon (24 dp, alpha only)'))
# Favicon / web
write('favicon.svg', svg(64, 64, f'<rect width="64" height="64" rx="14" fill="{NIGHT}"/>' + loop(32, 32, 18, 7, segs=24), title='driveperf.com favicon'))
