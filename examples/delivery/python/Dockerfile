FROM ubuntu:jammy

# install prerequisites
RUN apt update
RUN apt install python3 -y
RUN apt install pip -y
RUN apt install pkg-config libssl-dev -y 
RUN pip install requests
RUN pip install python-qpid-proton

# add certificates and script
ADD chain_and_key.pem .
ADD ca.pem .
ADD main.py .

# execute script 
CMD python3 main.py