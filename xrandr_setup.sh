#!/bin/bash

output="$1"
mode="$2"
direction="$3"
reference="$4"

help() {
  printf "usage:\nsetup output|auto [w]x[h]_[r]+[x]+[y] [direction][-of] [output|primary]\n"
  printf "\tdirection: one of [right-of, left-of, above, below]\n"
}

get_available_output() {
  virtual="$(xrandr -q | grep disconnected | grep VIRTUAL | head -n1 | cut -f1 -d ' ')"
  unused="$virtual"
  [ -z "$unused" ] && unused="$(xrandr -q | grep disconnected | head -n1 | cut -f1 -d ' ')"

  echo "$unused"
}

# check_output
[ -z "$output" ] && echo "no output selected" && help && exit 1

if [ "$output" == "auto" ]; then
  output="$(get_available_output)"

  [ -z "$ouput" ] && echo "no outputs available" && exit 1
fi
# turn off if was running
xrandr 2>/dev/null --output "$output" --off

if xrandr -q | grep -q "$output connected"; then
  xrandr --output "$output" --off
fi

# check_reference
[ -z "$reference" ] && echo "no reference output selected" && help && exit 1

if [ "$reference" == "primary" ]; then
  reference="$(xrandr -q | grep primary | cut -d' ' -f1)"

  [ -z "$reference" ] && echo "no primary reference output available" && exit 1
fi

xrandr -q | grep -q "$reference disconnected" && echo "reference output is not active" && help && exit 1

# check_direction
[ -z "$direction" ] && echo "no direction provided" && help && exit 1

direction="$(printf "left-of\nright-of\nabove\nbelow" | grep -F "$direction")"
[ -z "$direction" ] && echo "invalid direction ($direction) provided" && help && exit 1

# check_mode
[ -z "$mode" ] && echo "no mode provided" && help && exit 1

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

[ -z "$w" ] && echo "invalid mode: no width provided" && help && exit 1
[ -z "$h" ] && echo "invalid mode: no height provided" && help && exit 1
[ -z "$r" ] && echo "invalid mode: no refresh rate provided" && help && exit 1

printf "settings:\n\toutput: $output\n\tmode: $mode\n\tdirection: $direction\n\tclip: ${clip}\n\treference: $reference\n"

modeline="$(cvt "$w" "$h" "$r" | sed -n 's/.*Modeline "\([^" ]\+\)" \(.*\)/\1 \2/p')"
name="$(echo "${modeline}" | sed 's/\([^ ]\+\) .*/\1/')"
[[ -z "${modeline}" || -z "$name" ]] && echo "Error! modeline='${modeline}' name='$name'" && exit 1

xrandr 2>/dev/null --delmode "$output" "$name"
xrandr 2>/dev/null --rmmode "$name"

set -e
xrandr --newmode ${modeline}
xrandr --addmode "$output" "$name"

# Enable the display
xrandr --output "$output" --mode "$name" --"$direction" "$reference"

# [ ! -f "$HOME/.vnc/passwd" ] && x11vnc -storepasswd "$HOME/.vnc/passwd"

# clip="$(xrandr | grep "^$DEVICE.*$" | grep -o '[0-9]*x[0-9]*+[0-9]*+[0-9]*')"
# x11vnc -usepw -clip xinerama1 -multiptr -xkb # -ssl SAVE
sleep 3
# arandr &
x11vnc -nounixpw -usepw -xvnc -avahi -clip xinerama1 -shared -ungrabboth
# -ssl SAVE-jannec
# ssh -t -L 5900:localhost:5900 192.168.240.88 'x11vnc -clip "$clip" -noxinerama -noxrandr -nevershared -usepw -multiptr -xkb'

# vncviewer -PreferredEncoding=ZRLE localhost:0

xrandr 2>/dev/null --output "$output" --off
xrandr 2>/dev/null --delmode "$output" "$name"
xrandr 2>/dev/null --rmmode "$name"
