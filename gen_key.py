import key_utils
import sys

if len(sys.argv) < 2:
    print("specify the file name as the first param")
    exit(1)

with open(sys.argv[1], "wb") as file:
    key = key_utils.newkey()
    file.write(key)
