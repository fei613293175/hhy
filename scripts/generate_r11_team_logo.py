from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


SIZE = 1024
SCALE = 3


def scaled(value: int) -> int:
    return value * SCALE


def draw_logo() -> Image.Image:
    canvas = Image.new("RGB", (scaled(SIZE), scaled(SIZE)), "#F7FAFF")
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (scaled(116), scaled(126), scaled(908), scaled(918)),
        radius=scaled(176),
        fill=(37, 105, 206, 34),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(scaled(30)))
    canvas.paste(shadow, (0, 0), shadow)

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(
        (scaled(102), scaled(102), scaled(922), scaled(922)),
        radius=scaled(184),
        fill="#EAF3FF",
    )
    draw.ellipse(
        (scaled(242), scaled(238), scaled(782), scaled(778)),
        fill="#D6E9FF",
    )

    # Connected collaboration path and growth arrow.
    path_points = [
        (scaled(278), scaled(662)),
        (scaled(422), scaled(552)),
        (scaled(546), scaled(606)),
        (scaled(714), scaled(424)),
    ]
    draw.line(path_points, fill="#1677FF", width=scaled(42), joint="curve")
    draw.polygon(
        [
            (scaled(710), scaled(328)),
            (scaled(810), scaled(430)),
            (scaled(736), scaled(444)),
            (scaled(700), scaled(516)),
        ],
        fill="#1677FF",
    )

    # Three people remain legible in both square and circular crops.
    people = (
        (330, 465, "#20B8D8"),
        (514, 388, "#1677FF"),
        (676, 516, "#FF7968"),
    )
    for cx, cy, color in people:
        draw.ellipse(
            (scaled(cx - 66), scaled(cy - 66), scaled(cx + 66), scaled(cy + 66)),
            fill=color,
        )
        draw.rounded_rectangle(
            (scaled(cx - 112), scaled(cy + 62), scaled(cx + 112), scaled(cy + 196)),
            radius=scaled(66),
            fill=color,
        )

    # Neutral highlights add depth without introducing text or UI-like content.
    draw.ellipse(
        (scaled(448), scaled(322), scaled(482), scaled(356)),
        fill="#FFFFFF",
    )
    draw.ellipse(
        (scaled(292), scaled(405), scaled(320), scaled(433)),
        fill="#FFFFFF",
    )
    draw.ellipse(
        (scaled(640), scaled(460), scaled(668), scaled(488)),
        fill="#FFFFFF",
    )

    return canvas.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate the deterministic R11 team visual asset")
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("tests/android/fixtures/r11-team-logo.png"),
    )
    args = parser.parse_args()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    draw_logo().save(args.out, format="PNG", optimize=True)
    print(args.out)


if __name__ == "__main__":
    main()
