#!/bin/sh
# Regenerates all three packaged desktop icons from their SVG sources. Run it after editing
# either SVG:
#
#     desktopApp/desktopIcons/make_icons.sh
#
# macOS renders from app_icon_macos.svg, Windows and Linux from app_icon_rounded.svg — same
# rounded body, but without the shadow and the wide margin that only belong in the dock. See
# the comments at the top of each file.
#
# Needs Inkscape (the SVGs use clip paths and filters that macOS' own qlmanage renders but
# sips cannot rasterise) and Pillow for the .ico container. iconutil is part of macOS.
set -e
cd "$(dirname "$0")"

inkscape=/Applications/Inkscape.app/Contents/MacOS/inkscape
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# --- macOS ------------------------------------------------------------------------------
# iconutil wants every size as its own file, @2x included, in a .iconset directory.
outdir="$work/app_icon.iconset"
mkdir "$outdir"
for sz in 16 32 128 256 512; do
    echo "[+] Generate ${sz}x${sz} png..."
    $inkscape --export-filename "${outdir}/icon_${sz}x${sz}.png" -w $sz -h $sz app_icon_macos.svg
    $inkscape --export-filename "${outdir}/icon_${sz}x${sz}@2x.png" -w $((sz * 2)) -h $((sz * 2)) app_icon_macos.svg
done
iconutil --convert icns --output app_icon.icns "$outdir"
echo "[v] The icon is saved to app_icon.icns."

# --- Linux ------------------------------------------------------------------------------
echo "[+] Generate app_icon.png..."
$inkscape --export-filename app_icon.png -w 512 -h 512 app_icon_rounded.svg
echo "[v] The icon is saved to app_icon.png."

# --- Windows ----------------------------------------------------------------------------
# A .ico holds every size in one file, rendered once at 1024 and scaled down so all of them
# agree. Pillow stores each one as PNG rather than the older BMP; Windows has read PNG entries
# at every size since Vista, and the icon this replaced was a lone 256 PNG entry anyway.
echo "[+] Generate app_icon.ico..."
$inkscape --export-filename "$work/master.png" -w 1024 -h 1024 app_icon_rounded.svg
python3 -c '
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
Image.open(src).convert("RGBA").save(dst, sizes=sizes)
' "$work/master.png" app_icon.ico
echo "[v] The icon is saved to app_icon.ico."
