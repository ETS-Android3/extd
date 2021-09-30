#!/bin/sh

xrandr --output "$1" --off
xrandr --delmode "$1" $2
xrandr --rmmode $2