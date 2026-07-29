inkscape=/Applications/Inkscape.app/Contents/MacOS/inkscape
# The macOS variant, not the square app_icon.svg the other platforms take: macOS draws app
# icons unmasked, so the rounded body has to be part of the artwork.
insvg=app_icon_macos.svg
output=app_icon
outdir=${output}.iconset

mkdir $outdir
for sz in 16 32 128 256 512
do
    echo "[+] Generate ${sz}x${sz} png..."
    $inkscape --export-filename ${outdir}/icon_${sz}x${sz}.png -w $sz -h $sz $insvg
    $inkscape --export-filename ${outdir}/icon_${sz}x${sz}@2x.png -w $((sz*2)) -h $((sz*2)) $insvg
done
iconutil --convert icns --output ${output}.icns ${outdir}
echo "[v] The icon is saved to ${output}.icns."
rm -rf ${outdir}