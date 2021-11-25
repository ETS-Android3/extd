#!/usr/bin/python3

import sys
import socket

address = ("localhost", __LISTENER_PORT__)

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    try:
        for line in sys.stdin:
            split = line.split(":")

            if len(split) == 6:
                width = int(split[2])
                height = int(split[3])
                password = split[4]
                secret = split[5]

                if split[0] == "extd" and split[1] == "conn":
                    s.sendto(f'extd:spawn:{width}:{height}:{password}:{secret.strip()}'.encode(
                        "utf-8"), address)

                    data, address = s.recvfrom(512)
                    data = data.decode("utf-8")

                    print(data)
                    break

            break

        s.sendto(f'extd:bad_request'.encode("utf-8"), address)

    except socket.timeout:
        s.sendto("extd:ssh_wait:timed_out".encode("utf-8"), address)
        print("extd:ssh_wait:timed_out")
    except:
        s.sendto("extd:unknown_error".encode("utf-8"), address)
        print("extd:unknown_error")
