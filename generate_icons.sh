#!/bin/bash
SRC="/Users/justin/Downloads/ChatGPT Image May 3, 2026, 09_35_37 PM.png"
RES="app/src/main/res"

generate() {
    local dir=$1
    local size=$2
    mkdir -p "$RES/$dir"
    sips -z $size $size "$SRC" --out "$RES/$dir/ic_launcher.png" > /dev/null
    echo "Generated $RES/$dir/ic_launcher.png (${size}x${size})"
}

generate mipmap-mdpi     48
generate mipmap-hdpi     72
generate mipmap-xhdpi    96
generate mipmap-xxhdpi   144
generate mipmap-xxxhdpi  192

# TV banner: 320x180
mkdir -p "$RES/drawable-xhdpi"
sips -z 180 320 "$SRC" --out "$RES/drawable-xhdpi/app_banner.png" > /dev/null
echo "Generated TV banner drawable-xhdpi/app_banner.png (320x180)"

echo "Done."
