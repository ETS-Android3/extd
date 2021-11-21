#!/usr/bin/bash

while IFS= read -r line; do
    IFS=":"
    read -rasplit <<<"$line"

    if [ "${#split[@]}" == "4" ]; then
        echo x11vnc -once -timeout 30 -localhost -o /usr/share/extd/log.log -threads -passwd "${split[3]}" -clip "${split[1]}x${split[2]}+0+0"
        /usr/bin/x11vnc -once -timeout 30 -localhost -o /usr/share/extd/log.log -threads -passwd "${split[3]}" -clip "${split[1]}x${split[2]}+0+0"
    fi
done
