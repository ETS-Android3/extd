#!/usr/bin/python3

from re import sub
import sys
import random
from cryptography.fernet import Fernet
import netifaces
import ipaddress
import subprocess
import socket
from string import ascii_letters
import os
import datetime
import base64
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


def listen(port: int, secret: str, temp_key: str):
    daemon_addr = ("localhost", __DAEMON_PORT__)
    key = key_utils.load_key()

    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        try:
            message = f'extd:listen:{port}:{secret}:{os.getlogin()}:{temp_key}'
            # encrypt the message
            message = key.encrypt(message.encode("utf-8"))
            message = base64.b64encode(message)

            # request daemon to listen on port, for given secret
            s.sendto(message, daemon_addr)

            # daemon in listening
            # wait for response
            response = s.recvfrom(512)

            decoded = key.decrypt(base64.b64decode(response[0]))
            data = decoded.decode("utf-8")

            if data == "extd:accepted":  # daemon accepted client
                # daemon will give access for client to ssh
                # when it's done client will attempt to connect over ssh

                print("extd:listen:ok")
            else:
                print(f'extd:listen:error:({data})')

        except KeyboardInterrupt:
            print("extd:listen:cancelled")

        except Exception as e:
            print(f'extd:listen:unknown_error{str(e)}')


if len(sys.argv) < 2:
    print("not enough args")
    exit(1)

if sys.argv[1] == "add":
    try:
        subprocess.check_output(
            ["systemctl", "is-active", "--quiet", "--user", "extd.service"])

    except subprocess.CalledProcessError:
        print("daemon not running, run extd_manager daemon start")
        exit(1)

    lower, upper = get_ports()
    # port = random.randint(lower, upper)
    port = 4000
    # secret = get_random_string(12)
    secret = "secret"
    ips = get_ips()
    now = datetime.datetime.now()

    temp_key = key_utils.newkey().decode("utf-8")
    name = f'{socket.gethostname()}_{now.strftime("%m-%d-%Y")}'
    data = f'extd://{",".join(ips)}:{port}:{secret}:{name}:{temp_key}'

    subprocess.run(["qrencode", "-t", "UTF8", data])
    print(
        f'{name}\nconnect manually: \nips: {", ".join(ips)}\nport: {port}\nsecret: {secret}\nkey: {temp_key}\n')

    listen(port, secret, temp_key)

if sys.argv[1] == "daemon":
    if len(sys.argv) < 3:
        print("not enough args")
        exit(1)

    subprocess.run(
        ["systemctl", "--user", sys.argv[2], "extd.service"])
