import logging
import threading
import time
import sys
import os
import json
from proton.handlers import MessagingHandler
from proton.reactor import Container
from proton import SSLDomain

# Configuration by environment variables
ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM=os.environ.get("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
CA_CERTIFICATE_PEM=os.environ.get("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")

# Pre-known endpoint information
ENDPOINT_HOST=os.environ.get("ENDPOINT_HOST", "amqp_endpoint_host")
ENDPOINT_PORT=os.environ.get("ENDPOINT_PORT", "amqp_endpoint_port")
ENDPOINT_SOURCE=os.environ.get("ENDPOINT_SOURCE", "amqp_endpoint_source_address")

# ======== AMQP 1.0 CLIENT ========
def amqp_create_ssl_config():
	ssl_config = SSLDomain(SSLDomain.MODE_CLIENT)
	ssl_config.set_peer_authentication(SSLDomain.ANONYMOUS_PEER)
	ssl_config.set_credentials(cert_file=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, key_file=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM, password=None)
	ssl_config.set_trusted_ca_db(CA_CERTIFICATE_PEM)
	return ssl_config

class Receiver(MessagingHandler):
	def __init__(self, endpoint):
		super(Receiver, self).__init__()
		self.__endpoint = endpoint
	
	def on_start(self, event):
		logging.debug("Container reactor started")
		container = event.container
		endpoint = self.__endpoint

		# Step 1: connect
		ssl_config = amqp_create_ssl_config()
		amqp_url = "amqps://%s:%s" % (endpoint["host"], endpoint["port"])
		connection = container.connect(amqp_url, ssl_domain = ssl_config, reconnect = False, heartbeat = 5)

		# Step 2: create a receiving link using the source address of the endpoint
		container.create_receiver(connection, endpoint["source"])

	def on_message(self, event):
		# Decode binary body as UTF-8
		body_binary = event.message.body
		if isinstance(body_binary, bytes):
			body_text = body_binary.decode('utf-8')
		elif hasattr(body_binary, 'tobytes'):
			# Handle memory view objects
			body_text = body_binary.tobytes().decode('utf-8')
		else:
			body_text = str(body_binary)
		
		# Format application properties in sorted order
		app_props = {}
		if hasattr(event.message, 'properties') and event.message.properties:
			app_props = event.message.properties
		elif hasattr(event.message, 'application_properties') and event.message.application_properties:
			app_props = event.message.application_properties
		sorted_props_json = json.dumps(app_props, sort_keys=True)
		
		logging.info("Message received: body='%s', properties=%s", body_text, sorted_props_json)

def amqp_connect_and_listen(endpoint):
	receiver = Receiver(endpoint)
	container = Container(receiver)
	thread = threading.Thread(name = "AMQPClient", target = container.run, daemon = True)
	thread.start()
	while thread.is_alive():
		time.sleep(1)

# ======== DIRECT SUBSCRIBE WITH KNOWN ENDPOINT ========
def direct_subscribe():
	try:
		# Create endpoint from environment variables
		endpoint = {
			"host": ENDPOINT_HOST,
			"port": ENDPOINT_PORT,
			"source": ENDPOINT_SOURCE
		}
		
		logging.info("Using pre-known endpoint %s" % endpoint)
		amqp_connect_and_listen(endpoint)
		
	except Exception as e:
		logging.warning("An exception occurred while running direct_subscribe: %s" % e)

# ======== STARTUP AND RUN LOOP ========
def dump_config():
	logging.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'" % ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	logging.info("CA_CERTIFICATE_PEM: '%s'" % CA_CERTIFICATE_PEM)
	logging.info("ENDPOINT_HOST: '%s'" % ENDPOINT_HOST)
	logging.info("ENDPOINT_PORT: '%s'" % ENDPOINT_PORT)
	logging.info("ENDPOINT_SOURCE: '%s'" % ENDPOINT_SOURCE)

def configure_logging():
	logging.basicConfig(format = "%(asctime)s %(levelname)s %(message)s", level = logging.DEBUG)
	logging.getLogger("proton").setLevel(logging.INFO)

def main():
	configure_logging()
	logging.info("Starting application")
	dump_config()
	try:
		direct_subscribe()
	except KeyboardInterrupt:
		logging.info("Application stopped")

main()