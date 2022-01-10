import socket
import logging
import subprocess
from typing import Callable
import base64
import key_utils

logging.basicConfig(filename="extd.log", level=logging.INFO)


def spawn(width: str, height: str, password: str, then: Callable[[str], str]):
    try:
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
                    "-o",
                    "extd.log",
                    "-bg"
                ])
            then("extd:ok")

            return f'extd:spawn_status:{result}'

        except subprocess.CalledProcessError as e:
            print(f'extd:ssh_wait:spawn_error:{e.returncode}')
            return f'extd:ssh_wait:spawn_error:{e.returncode}'

        except socket.timeout:
            print("extd:ssh_wait:timed_out")
            return "extd:ssh_wait:timed_out"

    except KeyboardInterrupt:
        return "extd:ssh_wait:cancelled"

    except Exception as e:
        return f'extd:ssh_wait:unknown_error:{str(e)}'


def listen(port: int, secret: str, user: str):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("", port))
        logging.info(f'extd:daemon:listen listening for client on port {port}')

        try:
            # listens for the initial request from the device that wants to be added
            data, address = s.recvfrom(512)
            data = data.decode("utf-8")
            received_secret, key = data.split(":")
            logging.info(
                f'extd:daemon:listen got request from {address[0]}:{str(address[1])}')

            if received_secret == secret:
                entry = f'extd:add:{key.strip()}:{secret.strip()}:{user.strip()}:{address[0].strip()}'
                logging.info(f'extd:daemon:listen:add({entry})')

                # encrypt and pass to handler
                entry = private_key.encrypt(entry.encode("utf-8"))
                entry = base64.b64encode(entry)

                try:
                    out = subprocess.check_output(
                        ["/usr/bin/ssh", "-i", "__PRIVATE_SSH_KEY__", "extd@localhost"],
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
                        s.sendto(f'extd:ok:{__DAEMON_PORT__}'.encode(
                            "utf-8"), address)

                    else:
                        logging.error(f'extd:daemon:listen:error:{out}')

                    return out

                except subprocess.CalledProcessError as e:
                    msg = f'extd:daemon:listen:error:handler_request_add_returned={e.output.decode("utf-8")}'

                    logging.error(msg)
                    return msg

            else:
                logging.error("extd:daemon:listen invalid credentials",
                              received_secret, secret)
        except KeyboardInterrupt:
            return "extd:daemon:listen:cancelled"

        except Exception as e:
            return "extd:daemon:listen:unknown_error"


private_key = key_utils.load_key()
address = ("localhost", __DAEMON_PORT__)

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    s.bind(address)
    logging.info(f'extd:daemon extd listening on port {__DAEMON_PORT__}')

    while True:
        try:
            data, address = s.recvfrom(512)
            logging.info(f'got data: {data.decode("utf-8")}')

            decoded = private_key.decrypt(base64.b64decode(data))
            request = decoded.decode("utf-8").split(":")

            logging.info("extd:daemon got request from " +
                         address[0] + ":" + str(address[1]))

            if len(request) == 5 and request[0] == "extd" and request[1] == "listen":
                port = int(request[2])
                secret = request[3]
                user = request[4]

                result = listen(port, secret, user)
                logging.info(result)
                print(result)

                # encrypt and send back
                result = private_key.encrypt(result.encode("utf-8"))
                result = base64.b64encode(result)

                s.sendto(result, address)

            elif len(request) == 5 and request[0] == "extd" and request[1] == "spawn" and address[0] == "127.0.0.1":
                width = int(request[2])
                height = int(request[3])
                password = request[4]

                def then(result):
                    # encrypt and send back
                    result = private_key.encrypt(result.encode("utf-8"))
                    result = base64.b64encode(result)

                    s.sendto(result, address)

                result = spawn(width, height, password, then)
                logging.info(result)
                print(result)

            else:
                s.sendto("extd:bad_request".encode("utf-8"), address)
                logging.info("extd:daemon:bad_request")
                print("extd:daemon:bad_request")
        except KeyboardInterrupt:
            break

        except Exception as e:
            logging.error(f'extd:daemon:unknown_error:{str(e)}')
            print(f'extd:daemon:unknown_error:{str(e)}')
