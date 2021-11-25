import socket
import logging

logging.basicConfig(filename="__BASE_DIR__/extd.log", level=logging.INFO)


def listen(port: int, secret: str):
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.bind(("", port))
        logging.info(f'extd:listener listening for client on port {port}')

        try:
            data, address = s.recvfrom(512)
            data = data.decode("utf-8")
            received_secret, key = data.split(":")
            logging.info("extd:listener got request from " +
                         address[0] + ":" + str(address[1]))

            if received_secret == secret:
                r_authorized_keys = open(
                    "__BASE_DIR__/.ssh/authorized_keys", "r")
                authorized_keys = open(
                    "__BASE_DIR__/.ssh/authorized_keys", "a")
                entry = key + " extd@" + address[0] + "\n"

                found = False
                for line in r_authorized_keys:
                    if entry in line:
                        found = True
                        break

                if not found:
                    authorized_keys.write(entry)

                    logging.info("extd:listener wrote authorized_keys entry")
                else:
                    logging.info("extd:listener client already registered")

                s.sendto("extd:ok".encode("utf-8"), address)
                authorized_keys.close()
                r_authorized_keys.close()
                return "extd:accepted"

            else:
                logging.info("extd:listener invalid credentials",
                             received_secret, secret)
        except KeyboardInterrupt:
            return "extd:listener:cancelled"

        except:
            print("extd:listener:unknown_error")
            return "extd:listener:unknown_error"


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

            if len(request) == 4 and request[0] == "extd" and request[1] == "listen":
                port = int(request[2])
                secret = request[3]

                result = listen(port, secret)
                s.sendto(result.encode("utf-8"), address)
                logging.info("extd:daemon client accepted")

            else:
                s.sendto("extd:bad_request".encode("utf-8"), address)
                logging.info("extd:daemon bad_request")
        except KeyboardInterrupt:
            break

        except:
            print("extd:daemon:unknown_error")
