FROM ubuntu:18.04
FROM openjdk:11

FROM tomcat:8.5.49-jdk11-openjdk
RUN rm -rf /usr/local/tomcat/webapps/*
COPY ./target/*.war /usr/local/tomcat/webapps/siga.war
COPY ./src/main/resources/docker/setenv.sh /usr/local/tomcat/bin/setenv.sh
COPY ./src/main/resources/docker/server.xml /usr/local/tomcat/conf/server.xml
COPY ./src/main/resources/siga.p12 /usr/local/tomcat/conf/siga.p12
COPY ./src/main/resources/application.properties /usr/local/tomcat/conf/application.properties

CMD ["catalina.sh","run"]
