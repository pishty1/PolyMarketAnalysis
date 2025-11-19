import websocket
import json
import threading
import time
from confluent_kafka import Producer, KafkaError
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class PolymarketRTDSClient:
    """
    A Python client for the Polymarket Real-Time Data Socket (RTDS).

    This client handles the WebSocket connection, authentication, and subscription
    to various real-time data streams provided by Polymarket.
    """

    def __init__(self, url="wss://ws-live-data.polymarket.com", kafka_bootstrap_servers=None, kafka_topic="polymarket-messages"):
        """
        Initializes the PolymarketRTDSClient.

        Args:
            url (str): The WebSocket URL for the Polymarket RTDS.
            kafka_bootstrap_servers (str or list): Kafka bootstrap servers (e.g., 'localhost:9092' or ['broker1:9092', 'broker2:9092'])
            kafka_topic (str): Kafka topic to publish messages to
        """
        self.url = url
        self.ws = None
        self.ws_thread = None
        self.is_running = False
        self.connected = threading.Event()

        # Authentication credentials
        self.clob_auth = None
        self.gamma_auth = None

        # Kafka configuration
        self.kafka_topic = kafka_topic
        self.kafka_producer = None
        if kafka_bootstrap_servers:
            self._init_kafka_producer(kafka_bootstrap_servers)

    def _init_kafka_producer(self, bootstrap_servers):
        """Initialize Kafka producer with error handling."""
        try:
            conf = {
                'bootstrap.servers': bootstrap_servers,
                'client.id': 'polymarket-client',
                'acks': 'all',
                'retries': 3,
                'max.in.flight': 1
            }
            self.kafka_producer = Producer(conf)
            logger.info(f"Kafka producer initialized with bootstrap servers: {bootstrap_servers}")
        except Exception as e:
            logger.error(f"Failed to initialize Kafka producer: {e}")
            raise

    def _send_to_kafka(self, data):
        """Send message to Kafka."""
        if self.kafka_producer is None:
            logger.warning("Kafka producer not initialized. Message not sent to Kafka.")
            return

        def delivery_report(err, msg):
            """Called once for each message produced to indicate delivery result."""
            if err is not None:
                logger.error(f"Message delivery failed: {err}")
            else:
                logger.info(f"___Message sent to Kafka topic '{msg.topic()}' with key '{msg.key().decode('utf-8')}'")
                logger.info(f"_____________________________________________________________________________")

        try:
            # Extract topic from message if available, otherwise use default
            message_topic = data.get('topic', 'unknown')
            key = f"{message_topic}:{data.get('type', 'unknown')}"
            
            # Send message to Kafka
            # Trigger any available delivery report callbacks from previous produce() calls
            self.kafka_producer.poll(0)

            self.kafka_producer.produce(
                self.kafka_topic,
                key=key.encode('utf-8'),
                value=json.dumps(data).encode('utf-8'),
                callback=delivery_report
            )
            
        except Exception as e:
            logger.error(f"Unexpected error sending to Kafka: {e}")

    def _on_message(self, ws, message):
        """Callback function to handle incoming messages."""
        try:
            data = json.loads(message)
            logger.info(f"Received message: {data}")
            
            # Send to Kafka
            self._send_to_kafka(data)
            
        except json.JSONDecodeError:
            logger.warning(f"Received non-JSON message: {message}")

    def _on_error(self, ws, error):
        """Callback function to handle errors."""
        logger.error(f"WebSocket error: {error}")

    def _on_close(self, ws, close_status_code, close_msg):
        """Callback function to handle connection closure."""
        logger.info(f"WebSocket connection closed (status: {close_status_code}, msg: {close_msg})")
        self.is_running = False

    def _on_open(self, ws):
        """Callback function when the connection is opened."""
        logger.info("WebSocket connection opened")
        self.connected.set()

    def connect(self):
        """Establishes a WebSocket connection."""
        logger.info("Connecting to Polymarket RTDS...")
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
        logger.info(f"Subscribed to {topic}:{message_type}")

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
        logger.info(f"Unsubscribed from {topic}:{message_type}")

    def close(self):
        """Closes the WebSocket connection and Kafka producer."""
        if self.is_running:
            self.is_running = False
            self.ws.close()
            if self.ws_thread:
                self.ws_thread.join()
            logger.info("WebSocket connection closed.")
        
        # Close Kafka producer
        if self.kafka_producer:
            self.kafka_producer.flush()
            logger.info("Kafka producer flushed.")

import signal
import os

if __name__ == '__main__':
    # --- Example Usage ---

    # Get Kafka configuration from environment variables
    kafka_servers = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094")
    kafka_topic = os.environ.get("KAFKA_TOPIC", "polymarket-messages")

    # Create a client instance with Kafka
    client = PolymarketRTDSClient(
        kafka_bootstrap_servers=kafka_servers,
        kafka_topic=kafka_topic
    )

    # (Optional) Set CLOB Authentication
    # client.set_clob_authentication(
    #     api_key=os.environ.get("POLYMARKET_API_KEY"),
    #     api_secret=os.environ.get("POLYMARKET_API_SECRET"),
    #     passphrase=os.environ.get("POLYMARKET_PASSPHRASE")
    # )

    # (Optional) Set Gamma Authentication
    # client.set_gamma_authentication(wallet_address=os.environ.get("WALLET_ADDRESS"))

    # Connect to the RTDS
    client.connect()

    # Subscribe to comments
    client.subscribe(topic="comments", message_type="comment_created")
    # client.subscribe(topic="comments", message_type="reaction_created")

    def shutdown_handler(signum, frame):
        """Handles graceful shutdown on receiving SIGINT or SIGTERM."""
        logger.info(f"Received signal {signum}. Shutting down gracefully...")
        # The unsubscribe calls are not strictly necessary if the connection is closing,
        # but it's good practice to be explicit.
        client.unsubscribe(topic="comments", message_type="comment_created")
        # client.unsubscribe(topic="comments", message_type="reaction_created")
        client.close()

    # Register the shutdown handler for SIGINT (Ctrl+C) and SIGTERM
    signal.signal(signal.SIGINT, shutdown_handler)
    signal.signal(signal.SIGTERM, shutdown_handler)

    logger.info("Application started. Waiting for termination signal...")
    # Keep the main thread alive to receive messages
    while client.is_running:
        time.sleep(1)
    
    logger.info("Application has shut down.")