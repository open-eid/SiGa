FROM openjdk:17-slim-bullseye

ENV IGNITE_VERSION 2.15.0
ENV IGNITE_HOME /opt/ignite/apache-ignite-${IGNITE_VERSION}-bin

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/ignite

RUN curl https://archive.apache.org/dist/ignite/${IGNITE_VERSION}/apache-ignite-${IGNITE_VERSION}-bin.zip -o ignite.zip \
    && unzip ignite.zip \
    && rm ignite.zip

CMD $IGNITE_HOME/run.sh

EXPOSE 11211 47100 47500 49112
