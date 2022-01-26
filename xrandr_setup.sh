#!/bin/bash

set -e

mode="$1"
direction="$2"

[ -z "$mode" ] && echo "extd:error:no_mode_provided" && exit 1
[ -z "$direction" ] && echo "extd:error:no_direction_provided" && exit 1

get_available_output() {
  virtual="$(xrandr -q | grep disconnected | grep VIRTUAL | head -n1 | cut -f1 -d ' ')"
  unused="$virtual"
  [ -z "$unused" ] && unused="$(xrandr -q | grep disconnected | head -n1 | cut -f1 -d ' ')"

  echo "$unused"
}

output="$(get_available_output)"
[ -z "$output" ] && echo "extd:error:no_outputs" && exit 1

reference="$(xrandr -q | grep primary | cut -d' ' -f1)"
[ -z "$reference" ] && echo "extd:error:no_primary_display" && exit 1

xrandr -q | grep -q "$reference disconnected" && echo "extd:error:reference_display_disconnected" && exit 1

direction="$(printf "left-of\nright-of\nabove\nbelow" | grep -F "$direction")"
[ -z "$direction" ] && echo "extd:error:invalid_direction" && exit 1

res="$(echo "$mode" | cut -d'_' -f1)"

w="$(echo "$res" | cut -d'x' -f1)"
h="$(echo "$res" | cut -d'x' -f2)"
r="$(echo "$mode" | cut -d'_' -f2)"
primary_dim="$(xdpyinfo | awk '/dimensions/ {print $2}')"
wp="$(echo "$primary_dim" | cut -d'x' -f1)"
hp="$(echo "$primary_dim" | cut -d'x' -f2)"

# [ "$direction" == "left-of" ] && clip="${wp}x${hp}-$((wp*2))+0"
# [ "$direction" == "above" ] && clip="${wp}x${hp}+0-$((hp*2))"
# [ "$direction" == "below" ] && clip="${wp}x${hp}+0+${hp}"
# [ "$direction" == "right-of" ] && clip="${wp}x${hp}+${wp}+0"

[ -z "$w" ] && echo "extd:error:invalid_mode_no_width" && exit 1
[ -z "$h" ] && echo "extd:error:invalid_mode_no_height" && exit 1
[ -z "$r" ] && echo "extd:error:invalid_mode_no_refresh" && exit 1

modeline="$(cvt "$w" "$h" "$r" | sed -n 's/.*Modeline "\([^" ]\+\)" \(.*\)/\1 \2/p')"
name="$(echo "${modeline}" | sed 's/\([^ ]\+\) .*/\1/')"
[[ -z "${modeline}" || -z "$name" ]] && echo "extd:error:modeline_error" && exit 1

xrandr >/dev/null 2>&1 --delmode "$output" "$name"
xrandr >/dev/null 2>&1 --rmmode "$name"

set -e
xrandr >/dev/null 2>&1 --newmode ${modeline}
xrandr >/dev/null 2>&1 --addmode "$output" "$name"

# Enable the display
xrandr >/dev/null 2>&1 --output "$output" --mode "$name" --"$direction" "$reference"

echo "extd:ok"
