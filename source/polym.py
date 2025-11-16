import websocket
import json
import threading
import time

class PolymarketRTDSClient:
    """
    A Python client for the Polymarket Real-Time Data Socket (RTDS).

    This client handles the WebSocket connection, authentication, and subscription
    to various real-time data streams provided by Polymarket.
    """

    def __init__(self, url="wss://ws-live-data.polymarket.com"):
        """
        Initializes the PolymarketRTDSClient.

        Args:
            url (str): The WebSocket URL for the Polymarket RTDS.
        """
        self.url = url
        self.ws = None
        self.ws_thread = None
        self.is_running = False
        self.connected = threading.Event()

        # Authentication credentials
        self.clob_auth = None
        self.gamma_auth = None

    def _on_message(self, ws, message):
        """Callback function to handle incoming messages."""
        try:
            data = json.loads(message)
            print(f"Received message: {data}")
        except json.JSONDecodeError:
            print(f"Received non-JSON message: {message}")

    def _on_error(self, ws, error):
        """Callback function to handle errors."""
        print(f"Error: {error}")

    def _on_close(self, ws, close_status_code, close_msg):
        """Callback function to handle connection closure."""
        print("### Connection closed ###")
        self.is_running = False

    def _on_open(self, ws):
        """Callback function when the connection is opened."""
        print("### Connection opened ###")
        self.connected.set()

    def connect(self):
        """Establishes a WebSocket connection."""
        print("Connecting to Polymarket RTDS...")
        self.ws = websocket.WebSocketApp(
            self.url,
            on_open=self._on_open,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close
        )
        self.is_running = True
        self.connected.clear()
        # Use ping_interval to automatically send ping frames every 5 seconds
        self.ws_thread = threading.Thread(target=self.ws.run_forever, kwargs={"ping_interval": 5})
        self.ws_thread.daemon = True
        self.ws_thread.start()
        
        # Wait for connection to be established (timeout after 10 seconds)
        if not self.connected.wait(timeout=10):
            raise ConnectionError("Failed to establish WebSocket connection within 10 seconds")

    def set_clob_authentication(self, api_key, api_secret, passphrase):
        """
        Sets the CLOB authentication credentials.

        Args:
            api_key (str): Your Polymarket API key.
            api_secret (str): Your Polymarket API secret.
            passphrase (str): Your Polymarket API passphrase.
        """
        self.clob_auth = {
            "key": api_key,
            "secret": api_secret,
            "passphrase": passphrase
        }

    def set_gamma_authentication(self, wallet_address):
        """
        Sets the Gamma authentication credentials.

        Args:
            wallet_address (str): Your wallet address.
        """
        self.gamma_auth = {"address": wallet_address}

    def subscribe(self, topic, message_type, filters=None):
        """
        Subscribes to a data stream.

        Args:
            topic (str): The subscription topic (e.g., "crypto_prices").
            message_type (str): The message type/event (e.g., "update").
            filters (str, optional): An optional filter string. Defaults to None.
        """
        if not self.connected.is_set():
            raise ConnectionError("WebSocket is not connected")
        
        subscription = {
            "topic": topic,
            "type": message_type
        }
        if filters:
            subscription["filters"] = filters

        # Add authentication if set
        if self.clob_auth:
            subscription["clob_auth"] = self.clob_auth
        if self.gamma_auth:
            subscription["gamma_auth"] = self.gamma_auth

        message = {
            "action": "subscribe",
            "subscriptions": [subscription]
        }
        self.ws.send(json.dumps(message))
        print(f"Subscribed to {topic}:{message_type}")

    def unsubscribe(self, topic, message_type, filters=None):
        """
        Unsubscribes from a data stream.

        Args:
            topic (str): The subscription topic.
            message_type (str): The message type/event.
            filters (str, optional): An optional filter string. Defaults to None.
        """
        if not self.connected.is_set():
            raise ConnectionError("WebSocket is not connected")
        
        subscription = {
            "topic": topic,
            "type": message_type
        }
        if filters:
            subscription["filters"] = filters

        message = {
            "action": "unsubscribe",
            "subscriptions": [subscription]
        }
        self.ws.send(json.dumps(message))
        print(f"Unsubscribed from {topic}:{message_type}")

    def close(self):
        """Closes the WebSocket connection."""
        if self.is_running:
            self.is_running = False
            self.ws.close()
            if self.ws_thread:
                self.ws_thread.join()
            print("Connection closed.")

import signal
import os

if __name__ == '__main__':
    # --- Example Usage ---

    # Create a client instance
    client = PolymarketRTDSClient()

    # (Optional) Set CLOB Authentication
    # client.set_clob_authentication(
    #     api_key=os.environ.get("POLYMARKET_API_KEY"),
    #     api_secret=os.environ.get("POLYMARKET_API_SECRET"),
    #     passphrase=os.environ.get("POLYMARKET_PASSPHRASE")
    # )

    # (Optional) Set Gamma Authentication
    # client.set_gamma_authentication(wallet_address="YOUR_WALLET_ADDRESS")

    # Connect to the RTDS
    client.connect()

    # Subscribe to comments
    client.subscribe(topic="comments", message_type="comment_created")
    client.subscribe(topic="comments", message_type="reaction_created")

    def shutdown_handler(signum, frame):
        """Handles graceful shutdown on receiving SIGINT or SIGTERM."""
        print(f"Received signal {signum}. Shutting down gracefully...")
        # The unsubscribe calls are not strictly necessary if the connection is closing,
        # but it's good practice to be explicit.
        # client.unsubscribe(topic="crypto_prices", message_type="update")
        client.unsubscribe(topic="comments", message_type="comment_created")
        client.unsubscribe(topic="comments", message_type="reaction_created")
        client.close()

    # Register the shutdown handler for SIGINT (Ctrl+C) and SIGTERM
    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)

    print("Application started. Waiting for termination signal...")
    # Keep the main thread alive to receive messages
    while client.is_running:
        time.sleep(1)
    
    print("Application has shut down.")