import base64
import logging
import os
import random
import signal
import socket
import subprocess
import traceback
from typing import Callable

from cryptography.fernet import Fernet, InvalidToken

import key_utils

logging.basicConfig(filename="extd.log", level=logging.INFO)

daemonPort = __DAEMON_PORT__


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


def spawn(width: str, height: str, password: str, adb: bool, then: Callable[[str], str]):
    try:
        lower, upper = get_ports()
        port = random.randint(lower, upper)
        # authorized_keys = open("__USER_HOME_DIR__/.ssh/authorized_keys", "r")

        # found = False
        # for line in authorized_keys:
        #     if f' {user}:{secret}@{ip}' in line:
        #         found = True
        #         break

        # if not found:
        #     logging.info("extd:bad_request")
        #     return "extd:bad_request"

        logging.info(f'spawning server ({width}x{height}), {password}')

        # server needs to be started as the same user that owns the desktop
        # otherwise Xorg will not allow to open display :0
        # since Cookie will not match

        try:
            # -once -timeout 30 -localhost -o /usr/share/extd/log.log -threads -passwd "${split[3]}" -clip "${split[1]}x${split[2]}+0+0"
            adb_on = False

            if adb:
                try:
                    subprocess.call(
                        ["adb", "reverse", f'tcp:{port}', f'tcp:{port}'])
                    adb_on = True
                except subprocess.CalledProcessError:
                    logging.info("adb device not available")

            # adb reverse --remove tcp:5900
            result = subprocess.check_call(
                [
                    "/usr/bin/x11vnc",
                    "-once",
                    "-timeout",
                    "30",
                    "-localhost",
                    "-threads",
                    "-passwd",
                    password,
                    # "-clip",
                    # f'{width}x{height}+0+0',
                    # "-geometry",
                    # f'{width}x{height}',
                    "-rfbport",
                    str(port),
                    "-o",
                    "extd.log",
                    "-bg"
                ])
            then(f'extd:ok:{adb_on}:{port}')

            return f'extd:spawn_status:{result}:{adb_on}'

        except subprocess.CalledProcessError as e:
            return f'extd:ssh_wait:spawn_error:{e.returncode}'

        except socket.timeout:
            return "extd:ssh_wait:timed_out"

    except KeyboardInterrupt:
        return "extd:ssh_wait:cancelled"

    except Exception as e:
        return f'extd:ssh_wait:unknown_error:{str(e)}:{traceback.format_exc()}'


def listen(port: int, secret: str, user: str, temp_key: str):
    logging.info(f'extd:daemon:listen key: {temp_key}')
    temp_decryption_key = Fernet(temp_key)

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(120)
            s.bind(("", port))
            s.listen(1)
            logging.info(
                f'extd:daemon:listen listening for client on port {port}')

            # listens for the initial request from the device that wants to be added
            connection, address = s.accept()

            try:
                logging.info(
                    f'extd:daemon:listen got request from {address[0]}:{str(address[1])}')

                # read all at once. make sure it will all fit!!
                data = connection.recv(1024)

                logging.info(
                    f'---------------- {data}')
                data = temp_decryption_key.decrypt(data)
                data = data.decode("utf-8")

                received_secret, key_base64 = data.split(":")
                key = base64.b64decode(key_base64).decode("utf-8")

                if received_secret == secret:
                    entry = f'extd:add:{key.strip()}:{secret.strip()}:{user.strip()}:{address[0].strip()}'
                    logging.info(f'extd:daemon:listen:add({entry})')

                    # encrypt and pass to handler
                    entry = private_key.encrypt(entry.encode("utf-8"))
                    entry = base64.b64encode(entry)

                    out = subprocess.check_output(
                        ["/usr/bin/ssh", "-o", "StrictHostKeyChecking no",
                            "-i", "__PRIVATE_SSH_KEY__", "extd@localhost"],
                        input=f'daemon:{entry.decode("utf-8")}'.encode("utf-8")
                    )

                    out = out.strip()

                    logging.info(out)
                    # decrypt response
                    out = private_key.decrypt(base64.b64decode(out))
                    out = out.decode("utf-8")

                    if out == "extd:accepted":
                        logging.info("extd:daemon:listen:accepted")
                        # send result to the requesting device
                        connection.sendall(f'extd:ok:{daemonPort}'.encode(
                            "utf-8"))

                    else:
                        logging.error(f'extd:daemon:listen:error:{out}')

                    return out

                else:
                    logging.error("extd:daemon:listen invalid credentials",
                                  received_secret, secret)
            finally:
                connection.close()

    except TimeoutError:
        msg = f'extd:daemon:listen:timed_out'

        logging.info(msg)
        return msg

    except subprocess.CalledProcessError as e:
        msg = f'extd:daemon:listen:error:handler_request_add_returned={e.output.decode("utf-8")}'

        logging.error(msg)
        return msg

    except KeyboardInterrupt:
        return "extd:daemon:listen:cancelled"

    except InvalidToken as e:
        return f'extd:daemon:listen:invalid_token:{str(e)}'

    except Exception as e:
        return f'extd:daemon:listen:unknown_error:{str(e)}:{traceback.format_exc()}'


private_key = key_utils.load_key()
address = ("localhost", daemonPort)
pid_table = {}

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    s.bind(address)
    logging.info(f'extd:daemon extd listening on port {daemonPort}')

    while True:
        try:
            data, address = s.recvfrom(512)

            decoded = private_key.decrypt(base64.b64decode(data))
            request = decoded.decode("utf-8").split(":")

            logging.info("extd:daemon got request from " +
                         address[0] + ":" + str(address[1]))

            if len(request) == 6 and request[0] == "extd" and request[1] == "listen":
                port = int(request[2])
                secret = request[3]
                user = request[4]
                temp_key = request[5]

                pid = os.fork()

                if pid > 0:
                    pid_table[port] = pid
                if pid == 0:
                    result = listen(port, secret, user, temp_key)
                    logging.info(result)
                    print(result)

                    # encrypt and send back
                    result = private_key.encrypt(result.encode("utf-8"))
                    result = base64.b64encode(result)

                    s.sendto(result, address)

            elif len(request) == 6 and request[0] == "extd" and request[1] == "spawn" and address[0] == "127.0.0.1":
                width = int(request[2])
                height = int(request[3])
                password = request[4]
                adb = request[5] == "True"

                def then(result):
                    # encrypt and send back
                    result = private_key.encrypt(result.encode("utf-8"))
                    result = base64.b64encode(result)

                    s.sendto(result, address)

                pid = os.fork()

                if pid > 0:
                    pid_table[port] = pid
                if pid == 0:
                    result = spawn(width, height, password, adb, then)
                    logging.info(result)

            elif len(request) == 3 and request[1] == "cancel_listen" and address[0] == "127.0.0.1":
                port = int(request[2])

                if port in pid_table:
                    pid = pid_table[port]
                    os.kill(pid, signal.SIGKILL)
                    del pid_table[port]
                    logging.info(f'extd:daemon:killed_child:{pid}')

            else:
                s.sendto("extd:bad_request".encode("utf-8"), address)
                logging.info("extd:daemon:bad_request")

        except Exception as e:
            logging.error(
                f'extd:daemon:unknown_error:{str(e)}:{traceback.format_exc()}')
