#!/usr/bin/env bash
# tapt.sh "TEXT" [n] -> taps the n-th (default 1) item whose dumped label contains TEXT (case-insensitive); prints what it tapped
D=$(dirname "$0")
line=$(bash "$D/emulator-ui.sh" dump | grep -i -F -- "$1" | sed -n "${2:-1}p")
[ -z "$line" ] && { echo "NOT FOUND: $1"; exit 1; }
xy=${line##*@}; bash "$D/emulator-ui.sh" tap "${xy%,*}" "${xy#*,}"; echo "tapped: $line"
