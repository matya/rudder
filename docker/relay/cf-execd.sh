#!/bin/sh

set -e
set -x

# Needed because standard paths are symlinks

if [ ! -f /node_id/uuid.hive ]; then
  if [ -n "$RUDDER_RELAY_UUID" ]; then
    echo "$RUDDER_RELAY_UUID" > /node_id/uuid.hive
  else
    /opt/rudder/bin/rudder-uuidgen > /node_id/uuid.hive
  fi
fi

uuid=$(cat /node_id/uuid.hive)

if [ ! -f /agent_certs/ppkeys/localhost.priv ]; then
  mkdir -p /agent_certs/ppkeys
  if [ -n "$RUDDER_RELAY_RSA_KEY" ]; then
    echo "$RUDDER_RELAY_RSA_KEY" > /agent_certs/ppkeys/localhost.priv
    chmod 600 /agent_certs/ppkeys/localhost.priv
  else
    /opt/rudder/bin/cf-key -T 4096 -f /agent_certs/ppkeys/localhost
  fi
fi

# Generate public key based on private key so we can verify those two match togehter
openssl rsa -in /agent_certs/ppkeys/localhost.priv -RSAPublicKey_out > /tmp/verify.localhost.pub
if [ ! -f /agent_certs/ppkeys/localhost.pub ]; then
   mv /tmp/verify.localhost.pub /agent_certs/ppkeys/localhost.pub
else
   cmp /tmp/verify.localhost.pub /agent_certs/ppkeys/localhost.pub
fi

if [ ! -f /opt/rudder/etc/ssl/agent.cert ]; then
  mkdir -p /agent_certs/ssl
  if [ -n "$RUDDER_RELAY_CERTIFICATE" ]; then
    echo "$RUDDER_RELAY_CERTIFICATE" > /opt/rudder/etc/ssl/agent.cert
  else
    openssl req -new -sha256 -key /agent_certs/ppkeys/localhost.priv -out /agent_certs/ssl/agent.cert -passin "pass:Cfengine passphrase" -x509 -days 3650 -extensions agent_cert -config /opt/rudder/etc/ssl/openssl-agent.cnf -subj "/UID=${uuid}"
  fi
fi

# We verify that the certificate belongs to the private key (Modulus is identical)
openssl x509 -noout -modulus -in /opt/rudder/etc/ssl/agent.cert > /tmp/verify.modulus.crt
openssl rsa  -noout -modulus -in /agent_certs/ppkeys/localhost.priv > /tmp/verify.modulus.priv
cmp /tmp/verify.modulus.crt /tmp/verify.modulus.priv

rudder agent check -f

exec /opt/rudder/bin/cf-execd --no-fork --inform
