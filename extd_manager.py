#!/usr/bin/python3

import sys
import random
from Crypto.PublicKey import RSA
import netifaces
import ipaddress
import subprocess
import socket
from string import ascii_letters
import os
import datetime
import base64
import rsa
import key_utils


def get_ports():
    port_range = open("/proc/sys/net/ipv4/ip_local_port_range", "r")
    lower = 0
    upper = 0

    for line in port_range:
        l, u = line.split("\t")
        lower = int(l)
        upper = int(u)
        break

    port_range.close()

    return lower, upper


def get_random_string(length):
    letters = ascii_letters
    return ''.join(random.choice(letters) for i in range(length))


def get_ips():
    ips = []
    for iface in netifaces.interfaces():
        iface_details = netifaces.ifaddresses(iface)
        if netifaces.AF_INET in iface_details:
            for ip_interfaces in iface_details[netifaces.AF_INET]:
                if ipaddress.ip_address(ip_interfaces["addr"]).is_private and not str(ip_interfaces["addr"]).startswith("172.") and not str(ip_interfaces["addr"]).startswith("10.") and not str(ip_interfaces["addr"]).startswith("127."):
                    ips.append(ip_interfaces["addr"])

    return ips


def listen(port: int, secret: str, public_key):
    daemon_addr = ("localhost", __DAEMON_PORT__)

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            message = f'extd:listen:{port}:{secret}:{os.getlogin()}'.encode(
                "utf-8")
            # encrypt the message
            message = rsa.encrypt(message, public_key)
            message = base64.b64encode(message)

            # request daemon to listen on port, for given secret
            s.sendto(message, daemon_addr)

            # daemon in listening
            # wait for response
            response = s.recvfrom(512)
            data = response[0].decode("utf-8")

            if data == "extd:accepted":  # daemon accepted client
                # daemon will give access for client to ssh
                # when it's done client will attempt to connect over ssh

                print("extd:listen:ok")
            else:
                print(f'extd:error:({data})')

        except KeyboardInterrupt:
            print("extd:listen:cancelled")

        except Exception as e:
            print(f'extd:listen:unknown_error{str(e)}')


if len(sys.argv) < 2:
    print("not enough args")
    exit(1)

(priv, public_key) = key_utils.load_keys()

if sys.argv[1] == "add":
    lower, upper = get_ports()
    # port = random.randint(lower, upper)
    port = 4000
    # secret = get_random_string(12)
    secret = "secret"
    ips = get_ips()
    now = datetime.datetime.now()

    name = f'{socket.gethostname()}_{now.strftime("%m-%d-%Y")}'
    data = f'extd://{",".join(ips)}:{port}:{secret}:{name}'

    subprocess.run(["qrencode", "-t", "UTF8", data])
    print(
        f'{name}\nconnect manually: \nips: {", ".join(ips)}\nport: {port}\nsecret: {secret}\n')

    listen(port, secret, public_key)
