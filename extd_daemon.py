import socket
import logging
import subprocess

logging.basicConfig(filename="extd.log", level=logging.INFO)

def spawn(width: str, height: str, password: str, secret: str, ip: str):
    try:
        authorized_keys = open("__USER_HOME_DIR__/.ssh/authorized_keys", "r")

        found = False
        for line in authorized_keys:
            if f' {user}:{secret}@{ip}' in line:
                found = True
                break

        if not found:
            print("extd:bad_request")
            return "extd:bad_request"

        print(f'spawning server ({width}x{height}), {password}')

        # server needs to be started as the same user that owns the desktop
        # otherwise Xorg will not allow to open display :0
        # since Cookie will not match

        try:
            # -once -timeout 30 -localhost -o /usr/share/extd/log.log -threads -passwd "${split[3]}" -clip "${split[1]}x${split[2]}+0+0"
            subprocess.check_call(
                ["/usr/bin/x11vnc", "-once", "-timeout", "30", "-localhost", "-threads", "-passwd", password, "-clip", f'{width}x{height}+0+0', "-o", "extd.log"])
            
            return "extd:ok"

        except subprocess.CalledProcessError as e:
            print(f'extd:ssh_wait:spawn_error:{e.returncode}')
            return f'extd:ssh_wait:spawn_error:{e.returncode}'

        except socket.timeout:
            print("extd:ssh_wait:timed_out")
            return "extd:ssh_wait:timed_out"

    except KeyboardInterrupt:
        return "extd:ssh_wait:cancelled"

    except:
        return "extd:ssh_wait:unknown_error"


def listen(port: int, secret: str, user: str):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("", port))
        logging.info(f'extd:daemon:listen listening for client on port {port}')

        try:
            data, address = s.recvfrom(512)
            data = data.decode("utf-8")
            received_secret, key = data.split(":")
            logging.info(f'extd:daemon:listen got request from {address[0]}:{str(address[1])}')

            if received_secret == secret:
                entry = f'extd:add:{key.strip()}:{secret.strip()}:{user.strip()}:{address[0].strip()}'
                # logging.info(f'extd:daemon:listen:entry({entry})')

                out = subprocess.check_output(["/usr/bin/ssh", "extd@localhost"], input=entry.encode("utf-8")).strip().decode("utf-8")

                if out == "extd:accepted":
                    s.sendto("extd:ok".encode("utf-8"), address)
                    
                return out

            else:
                logging.info("extd:daemon:listen invalid credentials",
                             received_secret, secret)
        except KeyboardInterrupt:
            return "extd:daemon:listen:cancelled"

        except Exception as e:
            logging.error(f'extd:daemon:listen:unknown_error:{str(e)}')
            return "extd:daemon:listen:unknown_error"


address = ("localhost", __DAEMON_PORT__)

with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
    s.bind(address)
    logging.info(f'extd:daemon extd listening on port {__DAEMON_PORT__}')

    while True:
        try:
            data, address = s.recvfrom(512)
            data = data.decode("utf-8")
            request = data.split(":")
            logging.info("extd:daemon got request from " +
                         address[0] + ":" + str(address[1]))

            if len(request) == 5 and request[0] == "extd" and request[1] == "listen":
                port = int(request[2])
                secret = request[3]
                user = request[4]

                result = listen(port, secret, user)
                s.sendto(result.encode("utf-8"), address)
                logging.info(result)

            elif request[0] == "extd" and request[1] == "spawn" and address[0] == "127.0.0.1":
                width = int(request[2])
                height = int(request[3])
                password = request[4]
                received_secret = request[5]

                result = spawn(width, height, password, received_secret, address[0])
                s.sendto(result.encode("utf-8"), address)
                logging.info(result)

            else:
                s.sendto("extd:bad_request".encode("utf-8"), address)
                logging.info("extd:daemon:bad_request")
        except KeyboardInterrupt:
            break

        except Exception as e:
            logging.error(f'extd:daemon:unknown_error:{str(e)}')
