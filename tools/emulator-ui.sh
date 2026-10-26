#!/usr/bin/env bash
# ui.sh dump            -> the screen's text and clickable items with centre coordinates, one per line
# ui.sh shot NAME       -> a 432x960 screenshot at scratchpad/NAME.png
# ui.sh tap X Y | text "words" | back | swipeup | swipedown | crash
export MSYS_NO_PATHCONV=1
ADB="/d/Android/Sdk/platform-tools/adb.exe -s emulator-5554"
SP="C:/Users/Wi-Fi/AppData/Local/Temp/claude/C--Users-Wi-Fi-Apophenia/d49f80a5-ad09-4b12-a909-6c9a51e48d0e/scratchpad"
case "$1" in
  dump)
    $ADB shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
    $ADB exec-out cat /sdcard/u.xml | sed 's/<node /\n<node /g' | grep '^<node' | awk '
      function attr(n,   s) { if (match($0, " " n "=\"[^\"]*\"")) { s = substr($0, RSTART + length(n) + 3, RLENGTH - length(n) - 4); return s } return "" }
      { t = attr("text"); d = attr("content-desc"); c = attr("clickable"); b = attr("bounds")
        if (t == "") t = d
        if (t == "" && c != "true") next
        split(b, p, /[\[\],]+/)
        printf "%s%s @%d,%d\n", (c == "true" ? "[C] " : ""), t, (p[2] + p[4]) / 2, (p[3] + p[5]) / 2 }'
    ;;
  crash) $ADB logcat -d -b crash 2>/dev/null | grep -A3 "apophenia" | tail -12 ;;
  shot)
    $ADB exec-out screencap -p > "$SP/full.png"
    powershell.exe -NoProfile -Command "Add-Type -AssemblyName System.Drawing; \$i=[System.Drawing.Image]::FromFile('$SP/full.png'); \$b=New-Object System.Drawing.Bitmap 432,960; \$g=[System.Drawing.Graphics]::FromImage(\$b); \$g.InterpolationMode='HighQualityBicubic'; \$g.DrawImage(\$i,0,0,432,960); \$b.Save('$SP/$2.png'); \$i.Dispose()"
    echo "$SP/$2.png"
    ;;
  tap) $ADB shell input tap "$2" "$3"; sleep 1.5 ;;
  text) $ADB shell input text "$(echo "$2" | sed 's/ /%s/g')"; sleep 0.5 ;;
  back) $ADB shell input keyevent 4; sleep 1.2 ;;
  swipeup) $ADB shell input swipe 540 1700 540 700 300; sleep 1 ;;
  swipedown) $ADB shell input swipe 540 700 540 1700 300; sleep 1 ;;
esac
