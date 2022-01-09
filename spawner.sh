#!/bin/sh

width="$1"
height="$2"
pass="$3"
log="$4"
timeout="$5"

[ -z "$timeout" ] timeout="30"

/usr/bin/x11vnc -once -timeout "$timeout" -localhost -threads -passwd "$pass", -clip "${width}x${height}+0+0", -o "$log"
