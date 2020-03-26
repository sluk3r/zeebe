#!/bin/sh
# Wait until the keycloak server is up and running
while ! timeout 1 bash -c "echo > /dev/tcp/keycloak/8080"; do
  sleep 1
done

# Configure client
/opt/jboss/keycloak/bin/kcadm.sh config credentials --server http://keycloak:8080/auth --realm master --user admin --password admin

# Create a new realm
/opt/jboss/keycloak/bin/kcadm.sh create realms -s realm=zeebe -s enabled=true -o

# Create the proxy client
/opt/jboss/keycloak/bin/kcadm.sh create clients -r zeebe -s clientId=proxy -s enabled=true -s clientAuthenticatorType=client-secret -s secret=secret -s serviceAccountsEnabled=true

# Create our zeebe client
/opt/jboss/keycloak/bin/kcadm.sh create clients -r zeebe -s clientId=zeebe -s enabled=true -s clientAuthenticatorType=client-secret -s secret=secret -s serviceAccountsEnabled=true
