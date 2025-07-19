import logging
import threading
import requests
import time
import sys
import os
import json
from proton.handlers import MessagingHandler
from proton.reactor import Container
from proton import SSLDomain

# Configuration by environment variables
ACTOR_API_HOST=os.environ.get("ACTOR_API_HOST", "hostname_of_the_actor_api")
ACTOR_API_PORT=os.environ.get("ACTOR_API_PORT", "port_of_the_actor_api")
ACTOR_API_SUBSCRIPTION_SELECTOR=os.environ.get("ACTOR_API_SUBSCRIPTION_SELECTOR", "selector_of_the_subscription")
ACTOR_COMMON_NAME=os.environ.get("ACTOR_COMMON_NAME", "cn_of_the_actor_client_certificate")
ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM=os.environ.get("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM", "pem_with_x509_certificate_chain_and_private_key")
CA_CERTIFICATE_PEM=os.environ.get("CA_CERTIFICATE_PEM", "pem_with_x509_certificate")


# ======== ACTOR API FUNCTIONS ========
def api_url(endpoint):
    return "https://%s:%s/%s/%s" % (ACTOR_API_HOST, ACTOR_API_PORT, ACTOR_COMMON_NAME, endpoint) 

def api_get(endpoint):
    return requests.get(api_url(endpoint), verify=CA_CERTIFICATE_PEM, cert=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)

def api_post(endpoint, json_data):
    return requests.post(api_url(endpoint), None, json_data, verify=CA_CERTIFICATE_PEM, cert=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)

def api_delete(endpoint):
    return requests.delete(api_url(endpoint), verify=CA_CERTIFICATE_PEM, cert=ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)

def api_get_subscription(id):
    return api_get("subscriptions/%s" % id)

def api_delete_subscription(id):
    return api_delete("subscriptions/%s" % id)

def api_create_subscription():
    json_data = {
		"selector": ACTOR_API_SUBSCRIPTION_SELECTOR
    }
    return api_post("subscriptions", json_data)	

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

# ======== CREATE AND CONSUME A SUBSCRIPTION ========
def log_json(message, json_dict):
	logging.info("%s: %s" % (message, json.dumps(json_dict, indent=2)))

def subscribe_and_receive():
	try:
		# Step 1: create a subscription using the actor API
		subscription_create_response = api_create_subscription()
		subscription_create_response_json = subscription_create_response.json();
		log_json("Subscription create response", subscription_create_response_json)

		if subscription_create_response.ok:
			# Step 2: get the subscription status
			subscription_id = subscription_create_response_json["id"]
			subscription_status_response = api_get_subscription(subscription_id)
			subscription_status_response_json = subscription_status_response.json()
			log_json("Subscription %s status response" % subscription_id, subscription_status_response_json)
			subscription_status = subscription_status_response_json["status"]

			# Step 3: while the subscription status is "REQUESTED", keep getting the status
			while subscription_status == "REQUESTED":
				time.sleep(2)
				subscription_status_response = api_get_subscription(subscription_id)
				subscription_status_response_json = subscription_status_response.json()
				subscription_status = subscription_status_response_json["status"]

			log_json("Subscription %s status response" % subscription_id, subscription_status_response_json)
			
			# Step 4a: if the status is "CREATED", connect to the endpoint and start the AMQP receiver
			if subscription_status == "CREATED":
				# NOTE to keep things simple, this code assumes that this response contains exactly one endpoint!
				endpoint = subscription_status_response_json["endpoints"][0]
				logging.info("Using endpoint %s" % endpoint)
				amqp_connect_and_listen(endpoint)
			
			# Step 4b: if the status is not "CREATED" warn log and do nothing
			else:
				logging.warning("Unable to use subscription %s" % subscription_id)

	except Exception as e:
		logging.warning("An exception occurred while running subscribe_and_receive: %s" % e)

# ======== STARTUP AND RUN LOOP ========
def dump_config():
	logging.info("ACTOR_API_HOST: '%s'" % ACTOR_API_HOST);
	logging.info("ACTOR_API_PORT: '%s'" % ACTOR_API_PORT);
	logging.info("ACTOR_API_SUBSCRIPTION_SELECTOR: '%s'" % ACTOR_API_SUBSCRIPTION_SELECTOR);
	logging.info("ACTOR_COMMON_NAME: '%s'" % ACTOR_COMMON_NAME)
	logging.info("ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM: '%s'" % ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM)
	logging.info("CA_CERTIFICATE_PEM: '%s'" % CA_CERTIFICATE_PEM)

def configure_logging():
	logging.basicConfig(format = "%(asctime)s %(levelname)s %(message)s", level = logging.DEBUG)
	logging.getLogger("proton").setLevel(logging.INFO)

def main():
	configure_logging()
	logging.info("Starting application")
	dump_config()
	try:
		subscribe_and_receive()
	except KeyboardInterrupt:
		logging.info("Application stopped")

main()