#!/usr/bin/python3

import sys
import socket

daemonAddress = ("localhost", __DAEMON_PORT__)

try:
    handled = False

    for line in sys.stdin:
        split = line.split(":")

        if split[1] == "conn" and len(split) == 5:
            width = int(split[2])
            height = int(split[3])
            password = split[4]

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

                s.sendto(f'extd:spawn:{width}:{height}:{password}'.encode(
                    "utf-8"), daemonAddress)

                data, daemonAddress = s.recvfrom(512)
                data = data.decode("utf-8")

                print(data)
                handled = True
                break

        elif split[1] == "add" and len(split) == 6:
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
            print("extd:accepted")
            handled = True
            break

    if not handled:
        print(f'extd:bad_request:got("{line}")')

except socket.timeout:
    print("extd:ssh_wait:timed_out")
except Exception as e:
    print(f'extd:unknown_error:{str(e)}')
