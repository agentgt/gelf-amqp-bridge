# gelf-amqp-bridge

## Description
A bridge daemon for Graylog2 0.20 missing AMQP support.

This embarrasingly simple and probably and not scalable (single
threaded) single jar Java application will take GELF gzip messages from an AMQP queue
and then forward them on using Graylog2's UDP protocol.

## Build

```shell
mvn clean package
## move the jar to where ever you like
mv target/gelf-amqp-bridge-0.0.1-SNAPSHOT-jar-with-dependencies.jar /opt/gelf-amqp-bridge.jar
```


## Configure

Make a properties file like `/etc/gelf-amqp-bridge.properties` :

```properties
logFile=/var/log/gelf-amqp-bridge.log
logLevel=FINE
amqpURI=amqp://localhost
amqpQueue=gelf
graylog2Host=localhost
##graylog2Port=
```

## Run

```shell
java -jar /opt/gelf-amqp-bridge.jar /etc/gelf-amqp-bridge.properties
```
