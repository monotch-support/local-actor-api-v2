# Introduction

The main.py Python 3 script is a working example for directly connecting to a known AMQP endpoint for data subscription without using the Actor API to create a subscription. This is useful when you already have the endpoint information from a previous subscription creation or when working with persistent subscription endpoints. The script is not intended for production use.


# Prerequisites
  
 - Libraries Python 3
 - Python packages: python-qpid-proton 


# Adjust this according your information

 - ACTOR_CERTIFICATE_CHAIN_AND_KEY_PEM= *your complete crt chain*
 - CA_CERTIFICATE_PEM= *your full chain root.crt*
 - ENDPOINT_HOST= *AMQP endpoint hostname*
 - ENDPOINT_PORT= *AMQP endpoint port*
 - ENDPOINT_SOURCE= *AMQP source address for subscription*


# Howto run

 1. Set the ENV variables 
 2. Execute script with python 


# Example 

In the example below the script is executed in a docker container based on a default Ubuntu Jammy image.

The script uses the following fake configuration values:

- AMQP endpoint hostname: `amqp.my-interchange`
- AMQP endpoint port: `5671`
- AMQP source address: `subscription-source-address`
- Actor certificate chain and key file: `chain_and_key.pem`
- Certificate Authority certificate file: `ca.pem`

Since the default Ubuntu Jammy image needs additional packages installed for proton to support SSL, it includes the installation of the `pck-config` and `libssl-dev` (see `No SSL Support in proton` in the `Common mistakes` chapter).

```
FROM ubuntu:jammy

# install prerequisites
RUN apt update
RUN apt install python3 -y
RUN apt install pip -y
RUN apt install pkg-config libssl-dev -y 
RUN pip install python-qpid-proton

# add certificates and script
ADD chain_and_key.pem .
ADD ca.pem .
ADD main.py .

# execute script 
CMD python3 main.py
```


# Common mistakes

## No SSL support in proton

Sometimes the required depenencies are missing during the installation of the `python-qpid-proton` package causing the proton module to miss SSL support. This can be checked by running the following command: 
```
python3 -c "import proton; print('%s' % 'SSL present' if proton.SSL.present() else 'SSL NOT AVAILBLE')"
```

After reinstalling the missing dependencies the `python-qpid-proton` package would need to be reinstalled (rebuild) using `pip install python-qpid-proton --force-reinstall --no-cache-dir`.


## Certificate related

 - Not sending a full certificate chain (must include the client certificate, all intermediate certificates and the root certificate)
 - Not configuring the custom truststore (must include the root certificate)