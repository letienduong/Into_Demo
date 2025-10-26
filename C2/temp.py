# pwned_dict = {1: "ming@laptop@1705719549.9879712",
#               2: "john@desktop1@1705719551.1239704",
#               3: "maria@desktop2@1705719556.4474255"}

# print(pwned_dict)
# print(pwned_dict.items())
# print(*pwned_dict.items(), sep="\n")

from cryptography.fernet import Fernet
from base64 import urlsafe_b64encode

# This key must be 32 characters or fewer
KEY = "U can't touch this!"


def pad_key(key):
    """ This function will satisfy the Fernet encryption API requirement of having a full 32 character key. """
    while len(key) % 32 != 0:
        key += "G"
    return key


# Pad our key to 32 chars, byte encode it, urlsafe_b64 encode that, and then create the AES/CBC mode cipher object
cipher = Fernet(urlsafe_b64encode(pad_key(KEY).encode()))

token = cipher.encrypt(b"my deep dark secret")
print(type(token))
print(token)

print(cipher.decrypt(token))

# print(1 % 32)
# print(12 % 32)
# print(16 % 32)
# print(32 % 32)