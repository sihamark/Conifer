#!/usr/bin/env python3
"""
Generates the desktop app icons from the square app_icon.svg beside this script: the packaged
icons (.icns, .ico, .png) and the window icon per platform in
desktopApp/src/main/composeResources/drawable.

Renders with Quick Look (`qlmanage`), scales with Pillow and compiles the macOS icon with
`actool` (Xcode), so it only runs on macOS: `python3 desktopApp/desktopIcons/generate.py`

Each icon follows its platform's app icon grid instead of filling the canvas:
- macOS: `.icns` rendered by the system from an Icon Composer icon, so macOS 26+ accepts its
  shape and doesn't shrink it into a gray squircle
- Windows: 42 of 48 px square with 2 px corner radius (Windows 11 app icon grid)
- Linux: 104 of 128 px square with 8 px corner radius (Adwaita app icon template)
"""

from __future__ import annotations

import io
import json
import plistlib
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

from PIL import Image, ImageCms, ImageDraw

SOURCE_DIR = Path(__file__).resolve().parent
DESKTOP_APP = SOURCE_DIR.parent
DRAWABLE_DIR = DESKTOP_APP / "src/main/composeResources/drawable"
LSREGISTER = (
    "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework"
    "/Support/lsregister"
)

BACKGROUND = "#506527"
# The cone fills app_icon.svg edge to edge; on a plate it sits on the middle 90% like the
# artwork of the system's own icons
ARTWORK_SCALE = 0.9

SVG_HEADER = (
    '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">\n'
)


def artwork() -> str:
    """The paths of app_icon.svg, without its background, scaled onto a 1024 canvas."""
    source = (SOURCE_DIR / "app_icon.svg").read_text()
    paths = "\n".join(re.findall(r"<path\b.*?/>", source, re.DOTALL))
    scale = 1024 * ARTWORK_SCALE / 64
    offset = 1024 * (1 - ARTWORK_SCALE) / 2
    return f'  <g transform="translate({offset:g} {offset:g}) scale({scale:g})">\n{paths}\n  </g>\n'


def render(out_dir: Path) -> Image.Image:
    svg = out_dir / "app_icon_square.svg"
    svg.write_text(
        SVG_HEADER + f'  <rect width="1024" height="1024" fill="{BACKGROUND}"/>\n' + artwork() + "</svg>\n"
    )
    subprocess.run(
        ["qlmanage", "-t", "-s", "1024", "-o", str(out_dir), str(svg)],
        check=True,
        capture_output=True,
    )
    image = Image.open(out_dir / f"{svg.name}.png").convert("RGB")
    if image.size != (1024, 1024):
        raise SystemExit(f"{svg.name} rendered at {image.size}, expected 1024×1024")
    return image


def save_png(image: Image.Image, size: int, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.LANCZOS).save(path, optimize=True)


SUPERSAMPLING = 4


def rounded_mask(size: int, radius: float) -> Image.Image:
    big = size * SUPERSAMPLING
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=radius * SUPERSAMPLING, fill=255
    )
    return mask.resize((size, size), Image.LANCZOS)


def plate(image: Image.Image, canvas: int, body: int, mask: Image.Image) -> Image.Image:
    """Scales `image` to `body`, clips it with `mask` and centers it on a transparent `canvas`."""
    offset = (canvas - body) // 2
    result = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    tile = image.resize((body, body), Image.LANCZOS).convert("RGBA")
    tile.putalpha(mask)
    result.alpha_composite(tile, (offset, offset))
    return result


def srgb(color: str) -> str:
    channels = [int(color[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    return "srgb:" + ",".join(f"{c:.5f}" for c in channels) + ",1.00000"


def write_icon_composer(icon_dir: Path):
    """Icon Composer document with the cone as one layer, flat (no glass, shadow or translucency)."""
    assets = icon_dir / "Assets"
    assets.mkdir(parents=True)
    (assets / "cone.svg").write_text(SVG_HEADER + artwork() + "</svg>\n")
    document = {
        "fill": {"solid": srgb(BACKGROUND)},
        "groups": [
            {
                "layers": [{"image-name": "cone.svg", "name": "cone", "glass": False}],
                "shadow": {"kind": "none", "opacity": 0},
                "translucency": {"enabled": False, "value": 0},
            }
        ],
        "supported-platforms": {"squares": ["macOS"]},
    }
    (icon_dir / "icon.json").write_text(json.dumps(document, indent=2) + "\n")


def macos_iconset(tmp_dir: Path) -> Path:
    """Compiles the Icon Composer icon into a throwaway bundle and lets the system render it."""
    icon_dir = tmp_dir / "AppIcon.icon"
    write_icon_composer(icon_dir)
    bundle = tmp_dir / "AppIcon.app"
    resources = bundle / "Contents/Resources"
    resources.mkdir(parents=True)
    subprocess.run(
        [
            "xcrun", "actool", str(icon_dir),
            "--compile", str(resources),
            "--app-icon", "AppIcon",
            "--platform", "macosx",
            "--target-device", "mac",
            "--minimum-deployment-target", "10.13",
            "--output-partial-info-plist", str(tmp_dir / "partial.plist"),
        ],
        check=True,
        capture_output=True,
    )
    # Without an executable the system shows a placeholder instead of the icon
    executable = bundle / "Contents/MacOS/AppIcon"
    executable.parent.mkdir()
    executable.write_text("#!/bin/sh\n")
    executable.chmod(0o755)
    with open(bundle / "Contents/Info.plist", "wb") as info:
        plistlib.dump(
            {
                "CFBundleIdentifier": "eu.heha.conifer.icon-render",
                "CFBundlePackageType": "APPL",
                "CFBundleExecutable": "AppIcon",
                "CFBundleIconFile": "AppIcon",
                "CFBundleIconName": "AppIcon",
            },
            info,
        )
    iconset = tmp_dir / "app_icon.iconset"
    # The system only renders the icon of a bundle registered with Launch Services
    subprocess.run([LSREGISTER, "-f", str(bundle)], check=True)
    try:
        subprocess.run(
            ["swift", str(SOURCE_DIR / "render_bundle_icon.swift"), str(bundle), str(iconset)],
            check=True,
        )
    finally:
        subprocess.run([LSREGISTER, "-u", str(bundle)], check=True)
    # The placeholder for a missing icon is light gray; the real icon has the green background
    # The system renders in Display P3 and tags the PNGs with it, so compare in sRGB
    rendering = Image.open(iconset / "icon_512x512@2x.png")
    profile = ImageCms.ImageCmsProfile(io.BytesIO(rendering.info["icc_profile"]))
    srgb_rendering = ImageCms.profileToProfile(
        rendering.convert("RGB"), profile, ImageCms.createProfile("sRGB")
    )
    center = srgb_rendering.getpixel((200, 200))
    expected = Image.new("RGB", (1, 1), BACKGROUND).getpixel((0, 0))
    if max(abs(a - b) for a, b in zip(center, expected)) > 3:
        raise SystemExit(f"macOS rendered {center} instead of the background {expected}")
    return iconset


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        square = render(tmp_dir)

        icons = {
            "windows": plate(square, 1024, 896, rounded_mask(896, 1024 * 2 / 48)),
            "linux": plate(square, 1024, 832, rounded_mask(832, 1024 * 8 / 128)),
        }
        for name, icon in icons.items():
            save_png(icon, 512, DRAWABLE_DIR / f"app_icon_{name}.png")
        save_png(icons["linux"], 512, SOURCE_DIR / "app_icon.png")
        icons["windows"].save(
            SOURCE_DIR / "app_icon.ico",
            sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
        )

        iconset = macos_iconset(tmp_dir)
        subprocess.run(
            ["iconutil", "-c", "icns", str(iconset), "-o", str(SOURCE_DIR / "app_icon.icns")],
            check=True,
        )
        shutil.copy(iconset / "icon_512x512.png", DRAWABLE_DIR / "app_icon_macos.png")


if __name__ == "__main__":
    main()
