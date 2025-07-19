import logging
import threading
import time
import sys
import os
import json
from datetime import datetime
from proton.handlers import MessagingHandler
from proton.reactor import Container
from proton import SSLDomain, Message

# Configuration by environment variables
ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM=os.environ.get("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
CA_CERTIFICATE_PEM=os.environ.get("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")
MESSAGE_APPLICATION_PROPERTIES_JSON=os.environ.get("MESSAGE_APPLICATION_PROPERTIES_JSON", "message_application_properties_json")

# Pre-known endpoint information
ENDPOINT_HOST=os.environ.get("ENDPOINT_HOST", "amqp_endpoint_host")
ENDPOINT_PORT=os.environ.get("ENDPOINT_PORT", "amqp_endpoint_port")
ENDPOINT_TARGET=os.environ.get("ENDPOINT_TARGET", "amqp_endpoint_target_address")

# ======== AMQP 1.0 CLIENT ========
def amqp_create_ssl_config():
	ssl_config = SSLDomain(SSLDomain.MODE_CLIENT)
	ssl_config.set_peer_authentication(SSLDomain.ANONYMOUS_PEER)
	ssl_config.set_credentials(cert_file=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, key_file=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, password=None)
	ssl_config.set_trusted_ca_db(CA_CERTIFICATE_PEM)
	return ssl_config

class Sender(MessagingHandler):
	def __init__(self, endpoint):
		super(Sender, self).__init__()
		self.__endpoint = endpoint
		self.message_count = 0
	
	def on_start(self, event):
		logging.debug("Container reactor started")
		container = event.container
		endpoint = self.__endpoint

		# Step 1: connect
		ssl_config = amqp_create_ssl_config()
		amqp_url = "amqps://%s:%s" % (endpoint["host"], endpoint["port"])
		connection = container.connect(amqp_url, ssl_domain = ssl_config, reconnect = False, heartbeat = 5)

		# Step 2: create a sending link using the target address of the endpoint
		self.sender_link = container.create_sender(connection, endpoint["target"])
		
	def send_message(self):
		# Increment message counter
		self.message_count += 1
		# Create dynamic message content with counter and timestamp
		body_text = f"Hello World! Message #{self.message_count} at {datetime.now().strftime('%H:%M:%S')}"
		body_binary = body_text.encode('utf-8')
		message = Message()
		# Make sure to use the Data type for the body to ensure it is sent as binary
		message.inferred = True
		message.body = body_binary
		message.properties = json.loads(MESSAGE_APPLICATION_PROPERTIES_JSON)
		# Format properties in sorted order for consistent logging
		properties_dict = json.loads(MESSAGE_APPLICATION_PROPERTIES_JSON)
		sorted_properties = json.dumps(properties_dict, sort_keys=True)
		logging.info("Sending message: body='%s', properties=%s", body_text, sorted_properties)
		self.sender_link.send(message)

	def on_link_opened(self, event):
		# Send first message when link is opened (like Java onLinkRemoteOpen)
		if hasattr(event.link, 'credit') and event.link.credit > 0:
			self.send_message()

	def on_settled(self, event):
		# Schedule next message after 1 second
		event.container.schedule(1.0, self)

	def on_timer_task(self, event):
		# Send next message
		self.send_message()

def amqp_connect_and_publish(endpoint):
	sender = Sender(endpoint)
	container = Container(sender)
	thread = threading.Thread(name = "AMQPClient", target = container.run, daemon = True)
	thread.start()
	while thread.is_alive():
		time.sleep(1)

# ======== DIRECT PUBLISH WITH KNOWN ENDPOINT ========
def direct_publish():
	try:
		# Create endpoint from environment variables
		endpoint = {
			"host": ENDPOINT_HOST,
			"port": ENDPOINT_PORT,
			"target": ENDPOINT_TARGET
		}
		
		logging.info("Using pre-known endpoint %s" % endpoint)
		amqp_connect_and_publish(endpoint)
		
	except Exception as e:
		logging.warning("An exception occurred while running direct_publish: %s" % e)

# ======== STARTUP AND RUN LOOP ========
def dump_config():
	logging.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'" % ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	logging.info("CA_CERTIFICATE_PEM: '%s'" % CA_CERTIFICATE_PEM)
	logging.info("MESSAGE_APPLICATION_PROPERTIES_JSON: '%s'" % MESSAGE_APPLICATION_PROPERTIES_JSON)
	logging.info("ENDPOINT_HOST: '%s'" % ENDPOINT_HOST)
	logging.info("ENDPOINT_PORT: '%s'" % ENDPOINT_PORT)
	logging.info("ENDPOINT_TARGET: '%s'" % ENDPOINT_TARGET)

def configure_logging():
	logging.basicConfig(format = "%(asctime)s %(levelname)s %(message)s", level = logging.DEBUG)
	logging.getLogger("proton").setLevel(logging.INFO)

def main():
	configure_logging()
	logging.info("Starting application")
	dump_config()
	try:
		direct_publish()
	except KeyboardInterrupt:
		logging.info("Application stopped")

main()