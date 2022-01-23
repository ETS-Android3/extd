#!/usr/bin/python3

import base64
import logging
import socket
import sys

from cryptography.fernet import Fernet

logging.basicConfig(filename="extd.log", level=logging.INFO)


def load_key():
    with open("__PRIVATE_KEY__", mode='r') as priv:
        key = Fernet(priv.read())

    return key


private_key = load_key()

# pid = os.fork()

# if pid == 0:
try:
    handled = False

    for line in sys.stdin:
        split = line.split(":")

        # requests from the client are not encrypted
        if split[1] == "conn" and len(split) == 7:
            width = int(split[2])
            height = int(split[3])
            password = split[4]
            daemonAddress = ("localhost", int(split[5]))
            adb = split[6] == "true"

            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                # authorized_keys = open(
                # "__USER_HOME_DIR__/.ssh/authorized_keys", "r")
                # found = False
                # for line in authorized_keys:
                #     if f'{secret.strip()}@' in line:
                #         found = True
                #         break

                # if not found:
                #     print("extd:error:not_authorized")

                # authorized_keys.close()

                message = f'extd:spawn:{width}:{height}:{password}:{adb}'

                logging.info(message)
                # encrypt the message
                message = private_key.encrypt(message.encode("utf-8"))
                message = base64.b64encode(message)
                s.sendto(message, daemonAddress)
                data, daemonAddress = s.recvfrom(512)

                decoded = private_key.decrypt(base64.b64decode(data))
                data = decoded.decode("utf-8")

                print(data)
                handled = True
                break

        elif split[0] == "daemon":
            data = private_key.decrypt(base64.b64decode(split[1]))
            data = data.decode("utf-8")
            split = data.split(":")

            if split[1] == "add" and len(split) == 6:
                key = split[2]
                secret = split[3]
                user = split[4]
                ip = split[5]

                r_authorized_keys = open(
                    "__USER_HOME_DIR__/.ssh/authorized_keys", "r")
                authorized_keys = open(
                    "__USER_HOME_DIR__/.ssh/authorized_keys", "a")
                entry = f'{key.strip()} {user}:{secret}@{ip}\n'

                found = False
                for line in r_authorized_keys:
                    if entry in line:
                        found = True
                        break

                if not found:
                    authorized_keys.write(entry)

                authorized_keys.close()
                r_authorized_keys.close()

                message = "extd:accepted"
                logging.info(message)

                # encrypt the message
                message = private_key.encrypt(message.encode("utf-8"))
                message = base64.b64encode(message)

                print(message.decode("utf-8"))
                handled = True
                break

    if not handled:
        message = f'extd:bad_request:got("{line}")'
        logging.error(message)
        # encrypt the message
        message = private_key.encrypt(message.encode("utf-8"))
        message = base64.b64encode(message)
        print(message.decode("utf-8"))

except socket.timeout:
    message = "extd:ssh_wait:timed_out"
    logging.error(message)
    # encrypt the message
    message = private_key.encrypt(message.encode("utf-8"))
    print(message.decode("utf-8"))
except Exception as e:
    message = f'extd:unknown_error:{str(e)}'
    logging.error(message)
    # encrypt the message
    message = private_key.encrypt(message.encode("utf-8"))
    print(message.decode("utf-8"))
