FROM folioci/alpine-jre-openjdk11:latest

ENV VERTICLE_FILE mod-login-saml-fat.jar

ENV JAVA_OPTIONS -Dvertx.disableDnsResolver=true

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Copy your fat jar to the container
COPY target/${VERTICLE_FILE} ${VERTICLE_HOME}/${VERTICLE_FILE}

# Expose this port locally in the container.
EXPOSE 8081
