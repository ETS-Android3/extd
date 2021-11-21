from os import system
import socket
import sys

if len(sys.argv) < 3:
    print("not enough args")
    exit(1)

port = int(sys.argv[1])

s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("0.0.0.0", port))

while True:
    data, address = s.recvfrom(4096)
    data = data.decode("utf-8")
    secret, key = data.split(":")
    print("got request from " + address[0] + ":" + str(address[1]))

    if secret == sys.argv[2]:
        r_authorized_keys = open("/usr/share/extd/.ssh/authorized_keys", "r")
        authorized_keys = open("/usr/share/extd/.ssh/authorized_keys", "a")
        entry = key + " extd@" + address[0] + "\n"

        found = False
        for line in r_authorized_keys:
            if entry in line:
                found = True
                break

        if not found:
            authorized_keys.write(entry)
            s.sendto("ok".encode('utf-8'), address)
            print("ok")
        else:
            print("client already registered")

        authorized_keys.close()
        r_authorized_keys.close()

    else:
        print("invalid credentials", secret, sys.argv[2])
