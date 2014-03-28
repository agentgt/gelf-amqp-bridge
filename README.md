# gelf-amqp-bridge

## Description

A bridge daemon that simple forwards GELF messages from AMQP to Graylog2 UDP.
The reason I wrote this is because apparently Graylog2 0.20 is missing AMQP support.
Hopefull that will change soon.

This embarrasingly simple and probably and not scalable (single
threaded) single jar Java application will take GELF gzip messages from an AMQP queue
and then forward them on using Graylog2's UDP protocol.

The daemon currently expects the messages from AMQP to be already gzipped which will be the case if your using
**[gelfj](https://github.com/t0xa/gelfj)** latest AMQP support.


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
