from cryptography.fernet import Fernet


def newkey():
    return Fernet.generate_key()


def load_key():
    with open("__PRIVATE_KEY__", mode='r') as priv:
        key = Fernet(priv.read())

    return key
