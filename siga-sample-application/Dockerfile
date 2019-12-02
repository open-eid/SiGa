FROM ubuntu:18.04
FROM openjdk:11
ARG password=password
WORKDIR /
COPY ./target/*.jar sample-application.jar
CMD java -jar sample-application.jar
