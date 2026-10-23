import os, math
from PIL import Image, ImageDraw

ROOT = "/home/claude/icon-pack"

BG      = "#4A1B0C"
SAND    = "#FAC775"
ECHO    = "#EF9F27"
SPARK   = "#FAECE7"

CREST = [
    ("M", (0.0, 77.2)),
    ("L", (18.0, 77.2)),
    ("C", (25.2, 77.2, 28.4, 63.6, 36.0, 63.6)),
    ("C", (43.6, 63.6, 46.8, 80.4, 54.0, 80.4)),
    ("C", (61.2, 80.4, 64.8, 62.0, 72.0, 62.0)),
    ("C", (79.2, 62.0, 84.0, 75.6, 90.0, 75.6)),
    ("L", (108.0, 75.6)),
]

SPARKLE = [
    ("M", (54.0, 23.6)),
    ("Q", (57.6, 33.2, 67.6, 37.2)),
    ("Q", (57.6, 41.2, 54.0, 50.8)),
    ("Q", (50.4, 41.2, 40.4, 37.2)),
    ("Q", (50.4, 33.2, 54.0, 23.6)),
]

ECHO_NEAR_DY, ECHO_NEAR_W, ECHO_NEAR_A = 9.6, 2.8, 0.70
ECHO_FAR_DY,  ECHO_FAR_W,  ECHO_FAR_A  = 18.4, 1.8, 0.40


def shift(segs, dy):
    out = []
    for cmd, v in segs:
        out.append((cmd, tuple(v[i] - (dy if i % 2 else 0) for i in range(len(v)))))
    return out


def to_path_data(segs, close=False, tail=None):
    parts = []
    for cmd, v in segs:
        nums = ",".join(f"{n:g}" for n in v)
        if cmd == "M":
            parts.append(f"M{nums}")
        elif cmd == "L":
            parts.append(f"L{nums}")
        elif cmd == "C":
            parts.append(f"C{nums}")
        elif cmd == "Q":
            parts.append(f"Q{nums}")
    if tail:
        parts.append(tail)
    if close:
        parts.append("Z")
    return " ".join(parts)


def sample(segs, steps=48):
    pts, cur = [], None
    for cmd, v in segs:
        if cmd == "M":
            cur = (v[0], v[1]); pts.append(cur)
        elif cmd == "L":
            cur = (v[0], v[1]); pts.append(cur)
        elif cmd == "C":
            p0 = cur; c1 = (v[0], v[1]); c2 = (v[2], v[3]); p1 = (v[4], v[5])
            for i in range(1, steps + 1):
                t = i / steps; u = 1 - t
                x = u**3*p0[0] + 3*u*u*t*c1[0] + 3*u*t*t*c2[0] + t**3*p1[0]
                y = u**3*p0[1] + 3*u*u*t*c1[1] + 3*u*t*t*c2[1] + t**3*p1[1]
                pts.append((x, y))
            cur = p1
        elif cmd == "Q":
            p0 = cur; c = (v[0], v[1]); p1 = (v[2], v[3])
            for i in range(1, steps + 1):
                t = i / steps; u = 1 - t
                x = u*u*p0[0] + 2*u*t*c[0] + t*t*p1[0]
                y = u*u*p0[1] + 2*u*t*c[1] + t*t*p1[1]
                pts.append((x, y))
            cur = p1
    return pts


VECTOR_HEAD = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="108dp"\n'
               '    android:height="108dp"\n'
               '    android:viewportWidth="108"\n'
               '    android:viewportHeight="108">\n')


def body(sand_color, echo_color, spark_color):
    far = to_path_data(shift(CREST, ECHO_FAR_DY))
    near = to_path_data(shift(CREST, ECHO_NEAR_DY))
    dune = to_path_data(CREST, close=True, tail="L108,108 L0,108")
    spark = to_path_data(SPARKLE, close=True)
    return (
        f'    <path\n        android:pathData="{far}"\n'
        f'        android:strokeColor="{echo_color}"\n        android:strokeWidth="{ECHO_FAR_W}"\n'
        f'        android:strokeAlpha="{ECHO_FAR_A}"\n        android:strokeLineCap="round" />\n'
        f'    <path\n        android:pathData="{near}"\n'
        f'        android:strokeColor="{echo_color}"\n        android:strokeWidth="{ECHO_NEAR_W}"\n'
        f'        android:strokeAlpha="{ECHO_NEAR_A}"\n        android:strokeLineCap="round" />\n'
        f'    <path\n        android:pathData="{dune}"\n        android:fillColor="{sand_color}" />\n'
        f'    <path\n        android:pathData="{spark}"\n        android:fillColor="{spark_color}" />\n'
    )


def write(path, text):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(text)


write("res/drawable/ic_launcher_background.xml",
      VECTOR_HEAD + f'    <path\n        android:pathData="M0,0 H108 V108 H0 Z"\n'
      f'        android:fillColor="{BG}" />\n</vector>\n')

write("res/drawable/ic_launcher_foreground.xml",
      VECTOR_HEAD + body(SAND, ECHO, SPARK) + "</vector>\n")

write("res/drawable/ic_launcher_monochrome.xml",
      VECTOR_HEAD + body("#FFFFFF", "#FFFFFF", "#FFFFFF") + "</vector>\n")

adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            '</adaptive-icon>\n')
write("res/mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write("res/mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)

write("res/values/ic_launcher_colors.xml",
      '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
      f'    <color name="ic_launcher_background">{BG}</color>\n</resources>\n')


SS = 4  # supersampling


def render(canvas_px, bleed=True):
    """Render the 108x108 artwork at canvas_px, supersampled."""
    S = canvas_px * SS
    k = S / 108.0
    img = Image.new("RGBA", (S, S), BG)
    d = ImageDraw.Draw(img, "RGBA")

    def px(pts):
        return [(x * k, y * k) for x, y in pts]

    def rgba(hexs, alpha):
        h = hexs.lstrip("#")
        return tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (int(alpha * 255),)

    for dy, w, a in ((ECHO_FAR_DY, ECHO_FAR_W, ECHO_FAR_A),
                     (ECHO_NEAR_DY, ECHO_NEAR_W, ECHO_NEAR_A)):
        layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer, "RGBA")
        ld.line(px(sample(shift(CREST, dy))), fill=rgba(ECHO, a),
                width=max(1, int(round(w * k))), joint="curve")
        img.alpha_composite(layer)

    dune = sample(CREST) + [(108.0, 108.0), (0.0, 108.0)]
    d.polygon(px(dune), fill=rgba(SAND, 1.0))
    d.polygon(px(sample(SPARKLE)), fill=rgba(SPARK, 1.0))
    return img.resize((canvas_px, canvas_px), Image.LANCZOS)


def visible_crop(canvas_px):
    """Crop the central 72/108 region — the guaranteed-visible tile."""
    img = render(canvas_px)
    m = int(round(canvas_px * 18 / 108))
    return img.crop((m, m, canvas_px - m, canvas_px - m))


def rounded(img, ratio=0.2333):
    s = img.size[0]
    up = img.resize((s * SS, s * SS), Image.LANCZOS)
    mask = Image.new("L", (s * SS, s * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s * SS - 1, s * SS - 1],
                                           radius=int(s * SS * ratio), fill=255)
    up.putalpha(mask)
    return up.resize((s, s), Image.LANCZOS)


def circular(img):
    s = img.size[0]
    up = img.resize((s * SS, s * SS), Image.LANCZOS)
    mask = Image.new("L", (s * SS, s * SS), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, s * SS - 1, s * SS - 1], fill=255)
    up.putalpha(mask)
    return up.resize((s, s), Image.LANCZOS)


def tile(size):
    """Render the visible tile directly at `size` px, oversampled for quality."""
    return visible_crop(int(round(size * 108 / 72)))


DPI = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, s in DPI.items():
    base = tile(s)
    p = os.path.join(ROOT, f"res/mipmap-{name}")
    os.makedirs(p, exist_ok=True)
    rounded(base).save(os.path.join(p, "ic_launcher.png"))
    circular(base).save(os.path.join(p, "ic_launcher_round.png"))

os.makedirs(os.path.join(ROOT, "play-store"), exist_ok=True)
tile(512).convert("RGB").save(os.path.join(ROOT, "play-store/ic_launcher-playstore.png"))

# preview sheet
sizes = [192, 96, 72, 48]
W, H = 560, 260
sheet = Image.new("RGBA", (W, H), (255, 255, 255, 255))
x = 24
for s in sizes:
    t = rounded(tile(s))
    sheet.alpha_composite(t, (x, 24 + (192 - s) // 2))
    x += s + 24
sheet.convert("RGB").save(os.path.join(ROOT, "preview.png"))

print("done")
