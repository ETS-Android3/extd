#!/bin/bash

if [ "$1" == "add" ]; then
  read lower_port upper_port </proc/sys/net/ipv4/ip_local_port_range

  secret="secret"
  # secret="$(tr </dev/urandom -dc _A-Z-a-z-0-9 | head -c${1:-10} | base64 -w 0 | sed 's/==//')"
  port="4000"
  # port="$(comm -23 <(seq $lower_port $upper_port | sort) <(ss -Htan | awk '{print $4}' | cut -d':' -f2 | sort -u) | shuf | head -n 1)"
  ip="$(ip addr show | grep -Po 'inet \K[\d.]+' | grep -v '127.0.0.1')"

  echo "extd://$secret:$ip:$port" | qrencode -t UTF8
  printf "connect manually: \nip: $ip\nport: $port\nsecret: $secret\n"
  __BASE_DIR__/bin/listener "$port" "$secret"

elif [ "$1" == "daemon" ]; then
  if [ "$2" == "start" ]; then
    if [ ! -f __BASE_DIR__/daemon.pid ]; then
      __BASE_DIR__/bin/daemon
    fi

  elif [ "$2" == "stop" ]; then
    [ -f __BASE_DIR__/daemon.pid ] && kill -SIGINT "$(cat __BASE_DIR__/daemon.pid)"
  fi

elif [ "$1" == "revoke_access" ]; then
  cat __BASE_DIR__/.ssh/authorized_keys | grep -v "extd@" -o __BASE_DIR__/.ssh/authorized_keys
fi
