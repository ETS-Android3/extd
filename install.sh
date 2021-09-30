#!/bin/sh
HOMEDIR="/usr/share/extd"

useradd --system -d "$HOMEDIR" -m -s "$HOMEDIR"/run extd
cp run.sh "$HOMEDIR"/run
chmod +x "$HOMEDIR"/run

mkdir "$HOMEDIR"/.ssh
chmod 700 "$HOMEDIR"/.ssh

touch "$HOMEDIR"/.ssh/authorized_keys
chmod 644 "$HOMEDIR"/.ssh/authorized_keys

ssh-keygen -t rsa -b 4096 -f "$HOMEDIR"/.ssh/id_rsa -N ""
pip3 install segno
