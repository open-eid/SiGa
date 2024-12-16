<img src="docs/img/Co-funded_by_the_European_Union.jpg" width="350" height="200" alt="Co-funded by the European Union">

# SiGa - Signature Gateway

Signature Gateway is a web service for creating and signing ASIC-E containers and validating both ASIC-E and BDOC containers with XAdES signatures.

## Prerequisites:
For building and running SiGa you need Java 17.

## External services used by SiGa

* [Signature Validation Service](http://open-eid.github.io/SiVa/) for validating signatures.
* [MID REST service](https://github.com/SK-EID/MID/wiki) for signing with Mobile-ID (if enabled in configuration).
* [Smart-ID service](https://github.com/SK-EID/smart-id-documentation) for signing with Smart-ID (if enabled in configuration).
* TimeStamping service (based on configuration)
* (AIA) OCSP service (based on configuration)
* LOTL and national TSL services

### SiGa component model

![SiGa component model](docs/img/siga_component_model.png)

## How to build

SiGa project compiles into a JAR (Java archive) or WAR (Web application archive) file. The former one includes embedded
Tomcat, while the latter one requires a separate servlet container to run.

### Building JAR with embedded Tomcat

```bash
./mvnw clean install
```

### Building WAR for a separate servlet container

```bash
./mvnw clean install -Pwar
```

## How to deploy

### SiGa Deployment diagram

![SiGa deployment diagram](docs/img/siga_deployment.png)

In addition to a JAR or WAR file containing compiled SiGa, [Apache Ignite](https://ignite.apache.org/) version 2.15.0 is
required for session management.

### Running Apache Ignite

**Ignite servers must be up and running prior to SiGa startup.** Ignite servers must be
configured the same way as the Ignite client embedded in SiGa. An example Ignite configuration file can be seen
[here](docker/siga-ignite/ignite-configuration.xml).
Additionally, the following options must be added to the `JVM_OPTS` parameter in Ignite's `setenv.sh` file:
```bash
--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED
--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.time=ALL-UNNAMED
```
For general instructions, refer to [the official documentation](https://ignite.apache.org/docs/latest/quick-start/java) to
configure and run Ignite.

### Running SiGa

#### Running SiGa with embedded Tomcat

* Make [`application.properties`](#applicationproperties) available anywhere in the host system.
* Set $JAVA_OPTS environment variable with the required options (see more on
  [Ignite Getting Started guide](https://ignite.apache.org/docs/latest/quick-start/java#running-ignite-with-java-11-or-later)).
  Replace the path of `application.properties` in the following command to point to your own file.
  ```bash
  export JAVA_OPTS="-Dspring.config.location=file:/path/to/application.properties\
    --add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED\
    --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED\
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED\
    --add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED\
    --add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED\
    --add-opens=java.base/java.io=ALL-UNNAMED\
    --add-opens=java.base/java.nio=ALL-UNNAMED\
    --add-opens=java.base/java.util=ALL-UNNAMED\
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED\
    --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED\
    --add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED\
    --add-opens=java.base/java.lang=ALL-UNNAMED\
    --add-opens=java.base/java.time=ALL-UNNAMED\
    --add-opens=java.base/sun.security.x509=ALL-UNNAMED\
    --add-opens=java.base/java.security.cert=ALL-UNNAMED\
    -Djdk.tls.client.protocols=TLSv1.2"
  ```
* Run JAR file with SiGa webapp and embedded Tomcat (X.X.X denotes the version you are using):
  ```bash
  java $JAVA_OPTS -jar siga-webapp/target/siga-webapp-X.X.X.jar
  ```

#### Running SiGa in separate Tomcat installation

At first, Tomcat web servlet container needs to be downloaded. For example, version 8.5.46 could be downloaded with the
following command using `wget`:
```bash
wget https://www-eu.apache.org/dist/tomcat/tomcat-8/v8.5.46/bin/apache-tomcat-8.5.46.tar.gz
```

Unpack it somewhere:
```bash
tar -xzf apache-tomcat-8.5.46.tar.gz
```

Copy the built WAR file containing SiGa into Tomcat's `webapps` directory and start the servlet container:
```bash
cp SiGa/siga-webapp/target/siga-webapp-2.0.1.war apache-tomcat-8.5.46/webapps
./apache-tomcat-8.5.46/bin/catalina.sh run
```

* Make [`application.properties`](#applicationproperties) available anywhere in the host system.
* Depending on your system, it might be required to set the `JAVA_HOME` environment variable in file `/etc/default/tomcat8`. For example:
  * `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
* Create or modify `setenv.sh` placed inside Tomcat `bin` directory:
  * `export JAVA_OPTS="$JAVA_OPTS -Dspring.config.location=file:/path/to/application.properties"`
  * `export JAVA_OPTS="$JAVA_OPTS -Dspring.profiles.active=list-of-profiles-to-activate"` (see [available profiles](#available-profiles))

Additionally, the following options must be added to the `JAVA_OPTS` parameter in the same `setenv.sh` file (see more on
[Ignite Getting Started guide](https://ignite.apache.org/docs/latest/quick-start/java#running-ignite-with-java-11-or-later)):
```bash
--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED
--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED
--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.time=ALL-UNNAMED
--add-opens=java.base/sun.security.x509=ALL-UNNAMED
--add-opens=java.base/java.security.cert=ALL-UNNAMED
-Djdk.tls.client.protocols=TLSv1.2
```

### Available Spring profiles

| Profile name      | Description                                 |
|-------------------|---------------------------------------------|
| digidoc4jProd     | Use DD4J production mode                    |
| digidoc4jTest     | Use DD4J test mode (prefer AIA-OCSP)        |
| digidoc4jPerf     | Use DD4J test mode (without AIA-OCSP)       |
| mobileId          | Enable endpoints for signing with Mobile-ID |
| smartId           | Enable endpoints for signing with Smart-ID  |
| datafileContainer | Enable datafile container endpoints*        |

**NB:** exactly one of `digidoc4jProd`, `digidoc4jTest` and `digidoc4jPerf` must be active!

\* Datafile containers support has not been thoroughly performance tested. Use at your own risk.

## SiGa configuration

### `application.properties`

Example `application.properties` file with DEMO parameters can be seen [here](docker/siga-webapp/application.properties).
`application.properties` values must be changed for production mode, as default maven profile does not include it in the build.
Common Spring Boot properties are described [here](https://docs.spring.io/spring-boot/docs/2.7.7/reference/html/application-properties.html).

#### SiGa Ignite configuration

| Parameter                              | Mandatory | Description                                 | Example                              |
| -------------------------------------- | --------- | ------------------------------------------- | ------------------------------------ |
| siga.ignite.configuration-location     | Y         | Location of the ignite configuration file.  | `/path/to/ignite-configuration.xml`  |
| siga.ignite.application-cache-version  | Y         | Version of Ignite cache.                    | `v1`                                 |

Example `ignite-configuration.xml` file can be seen [here](docker/siga-ignite/ignite-configuration.xml).

#### SiGa DD4J configuration

| Parameter                         | Mandatory | Description                                                           | Example                    |
| --------------------------------- | --------- | --------------------------------------------------------------------- | -------------------------- |
| siga.dd4j.configuration-location  | Y         | Location of the DD4J configuration file.                              | `/path/to/digidoc4j.yaml`  |
| siga.dd4j.tsl-refresh-job-cron    | Y         | Cron expression for the scheduled job that refreshes DD4J TSL cache.  | `0 0 3 * * *`              |

More about configuring DD4J [here](https://github.com/open-eid/digidoc4j/wiki/Questions-&-Answers#using-a-yaml-file-for-configuration).

#### SiGa SiVa configuration

| Parameter                      | Mandatory | Description                                                                                                                                                                          | Example                                                                |
|--------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| siga.siva.url                  | Y         | Signature validation service URL.                                                                                                                                                    | `https://siva-arendus.eesti.ee/V3`                                     |
| siga.siva.trust-store          | Y         | SiVa service truststore path.                                                                                                                                                        | `file:/path/to/trust-store.p12` or `classpath:path/to/trust-store.p12` |
| siga.siva.trust-store-password | Y         | SiVa service truststore password.                                                                                                                                                    | `changeit`                                                             |
| siga.siva.connection-timeout   | N         | Connection timeout for regular connections in ISO-8601 duration format `PnDTnHnMn.nS`. The input is truncated to millisecond precision. If not provided, defaults to system default. | `PT10S`                                                                |
| siga.siva.write-timeout        | N         | Write timeout for regular connections in ISO-8601 duration format `PnDTnHnMn.nS`. The input is truncated to millisecond precision. If not provided, defaults to system default.      | `PT10S`                                                                |
| siga.siva.read-timeout         | N         | Read timeout for regular connections in ISO-8601 duration format `PnDTnHnMn.nS`. The input is truncated to millisecond precision. If not provided, defaults to system default.       | `PT10S`                                                                |
| siga.siva.max-in-memory-size   | N         | Maximum size of data to be sent to SiVa. If not provided, defaults to 256KB. Note that the default size may not be enough for containers with dozens of signatures.                  | `5MB`                                                                  |

#### SiGa MID REST configuration

Applicable if `mobileId` profile is active.

| Parameter                         | Mandatory | Description                                                                                                                                                                                                                                                                                                               | Example                          |
|-----------------------------------| --------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------|
| siga.midrest.url                  | Y         | MID REST service URL.                                                                                                                                                                                                                                                                                                     | `https://tsp.demo.sk.ee/mid-api` |
| siga.midrest.allowed-countries    | N         | MID REST allowed countries.                                                                                                                                                                                                                                                                                               | `EE, LT`                         |
| siga.midrest.truststore-path      | Y         | MID REST PKCS12 truststore path.                                                                                                                                                                                                                                                                                          | `mid_truststore.p12`             |
| siga.midrest.truststore-password  | Y         | MID REST PKCS12 truststore password.                                                                                                                                                                                                                                                                                      | `changeIt`                       |
| siga.midrest.long-polling-timeout | N         | MID REST [session status request](https://github.com/SK-EID/MID#334-long-polling) long poll value in milliseconds. Defaults to `30000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)  | `30000`                          |
| siga.midrest.connect-timeout      | N         | MID REST client connection timeout in milliseconds. Defaults to `5000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)                                                                  | `5000`                           |
| siga.midrest.status-polling-delay | N         | Delay before polling status in milliseconds. Defaults to `6000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)                                                                         | `6000`                           |

**NB:** MID REST relying party name and UUID are registered per [service](#siga_service).

#### SiGa Smart-ID configuration

Applicable if `smartId` profile is active.

| Parameter                                         | Mandatory | Description                                                                                                                                                                                                                                                                                                                                                        | Example                                  |
|---------------------------------------------------| --------- |--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|
| siga.sid.url                                      | Y         | Smart-ID service URL.                                                                                                                                                                                                                                                                                                                                              | `https://sid.demo.sk.ee/smart-id-rp/v2/` |
| siga.sid.session-status-response-socket-open-time | N         | Smart-ID [session status request](https://github.com/SK-EID/smart-id-documentation/blob/master/README.md#46-session-status) long poll value in milliseconds. Defaults to `30000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration) | `30000`                                  |
| siga.sid.connect-timeout                          | N         | Smart-ID client connection timeout in milliseconds. Defaults to `5000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)                                                                                                           | `5000`                                   |
| siga.sid.status-polling-delay                     | N         | Delay before polling status in milliseconds. Defaults to `6000`. [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)                                                                                                                  | `6000`                                   |
| siga.sid.allowed-countries                        | N         | Smart-ID allowed countries. Defaults to `EE, LT, LV`.                                                                                                                                                                                                                                                                                                              | `EE, LV, LT`                             |
| siga.sid.interaction-type                         | N         | Smart-ID [interaction](https://github.com/SK-EID/smart-id-documentation#31-uc-x-interaction-choice-realization) to be requested to be performed by the Smart-ID app. Supported options: `DISPLAY_TEXT_AND_PIN`, `VERIFICATION_CODE_CHOICE`. Defaults to `DISPLAY_TEXT_AND_PIN`.                                                                                    | `VERIFICATION_CODE_CHOICE`               |
| siga.sid.truststore-path                          | Y         | Smart-ID PKCS12 truststore path                                                                                                                                                                                                                                                                                                                                    | `sid_truststore.p12`                     |
| siga.sid.truststore-password                      | Y         | Smart-ID PKCS12 truststore password                                                                                                                                                                                                                                                                                                                                | `changeIt`                               |

**NB:** Smart-ID relying party name and UUID are registered per [service](#siga_service).

#### SiGa MID/SID signature/certificate status request re-processing configuration

MID/SID signature/certificate status requests and signature finalization steps are performed in background process. Following configuration parameters define how these steps are re-processed if exception occurs.

| Parameter                                        | Mandatory | Description                                                                                                                                                                                                                                                                                                                                                                                                                          | Example  |
|--------------------------------------------------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| siga.status-reprocessing.fixed-rate              |  N        | Failed signature/certificate status re-processing interval in milliseconds. Default value in milliseconds: `5000`                                                                                                                                                                                                                                                                                                                    | `5000`   |
| siga.status-reprocessing.initial-delay           |  N        | Initial delay on startup before re-processing signature/certificate status requests. Default value in milliseconds: `5000`                                                                                                                                                                                                                                                                                                           | `5000`   |
| siga.status-reprocessing.max-processing-attempts |  N        | Maximum failed processing attempts. Default value: `10`                                                                                                                                                                                                                                                                                                                                                                              | `10`     |
| siga.status-reprocessing.processing-timeout      |  N        | Maximum processing time, before request is considered failed and can be re-processed by other SiGa nodes. Used when request processing SiGa node fails or leaves Ignite topology. Default value in milliseconds: `30000` [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration)                            | `30000`  |
| siga.status-reprocessing.exception-timout        |  N        | Maximum time from last exception, before request is considered failed and can be re-processed by other SiGa nodes. Used when recoverable exception (e.g. networking) occurs and request can be re-processed. Default value in milliseconds: `5000`  [Supports ISO 8601 Duration format.](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration) | `5000`   |

#### SiGa security configuration

| Parameter                                            | Mandatory | Description                                                                                                                                                                                | Example                                         |
| ---------------------------------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| siga.security.hmac.expiration                        | Y         | Maximum amount of time from signing timestamp after which the request is considered expired, in seconds. Validation takes into account clock skew. Must be greater than or equal to `-1`.  | `5`                                             |
| siga.security.hmac.clock-skew                        | Y         | Maximum clock skew between SiGa server and service provider machines, in seconds. Must be greater than or equal to `0`.                                                                    | `2`                                             |
| siga.security.jasypt.encryption-algo                 | Y         | Algorithm that is used to encrypt service signing key values in service database.                                                                                                          | `PBEWITHSHA-256AND256BITAES-CBC-BC`             |
| siga.security.jasypt.encryption-key                  | Y         | Secret key that is used to encrypt/decrypt service signing key values in service database.                                                                                                 | `encryptorKey`                                  |
| siga.security.prohibited-policies-for-remote-signing | N         | Prohibited certificate policy OIDs for remote signing endpoint. Default values: 1.3.6.1.4.1.10015.1.3, 1.3.6.1.4.1.10015.18.1, 1.3.6.1.4.1.10015.17.2, 1.3.6.1.4.1.10015.17.1              | `1.3.6.1.4.1.10015.1.3, 1.3.6.1.4.1.10015.17.2` |

#### SiGa database configuration

Example changelogs and changesets are provided under `siga-auth/src/main/resources/db`. To apply a changelog to the database on the application startup, `spring.liquibase.change-log` property must be set, e.g.:

```
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
```

Use `classpath:db/changelog/db.changelog-master-dev.yaml` only for test/dev purposes. This changeset inserts default testing values into services database.

Out-of-the-box, SiGa supports **H2** and **PostgreSQL** databases. **H2** is good for development and testing, but in production using **PostgreSQL** is recommended.
An example for configuring SiGa to use PostgreSQL:

```
spring.sql.init.continue-on-error=false
spring.sql.init.platform=postgresql
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/database
spring.datasource.username=user
spring.datasource.password=password
```

#### SiGa health and heartbeat configuration

SiGa has built-in health endpoint for an overview of system related service statuses. The endpoint can be reached at `{host}/actuator/health`. An example configuration for health endpoint:
```
management.endpoint.health.show-details=ALWAYS
management.health.defaults.enabled=false
management.health.db.enabled=true
```

To add a heartbeat endpoint, the following configuration should be added to `application.properties`:
```
management.endpoints.web.exposure.include=health,heartbeat
management.endpoint.heartbeat.enabled=true
```
The heartbeat endpoint can be accessed at `{host}/actuator/heartbeat`.

To add version information endpoint, the following configuration should be added to `application.properties`:
```
management.endpoints.web.exposure.include=health,version
management.endpoint.version.enabled=true
```
The version information endpoint can be accessed at `{host}/actuator/version`.

## SiGa database

### Data model

#### SIGA_CLIENT

A table holding all the registered clients that are allowed to use SiGa.

| Column name    | Type                                | Description                 |
| -------------- | ----------------------------------- | --------------------------- |
| id             | SERIAL (autoincrement primary key)  | Entry ID                    |
| name           | VARCHAR(100)                        | Client name                 |
| contact_name   | VARCHAR(100)                        | Client contact person name  |
| contact_email  | VARCHAR(256)                        | Client contact e-mail       |
| contact_phone  | VARCHAR(30)                         | Client contact phone        |
| uuid           | VARCHAR(36)                         | Client UUID                 |
| created_at     | TIMESTAMP                           | Client creation date        |
| updated_at     | TIMESTAMP                           | Client update date          |

#### SIGA_SERVICE

A table holding all the registered services that are allowed to use SiGa.

| Column name                  | Type                                | Description                                                                                                   |
| ---------------------------- | ----------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| id                           | SERIAL (autoincrement primary key)  | Entry ID                                                                                                      |
| uuid                         | VARCHAR(36)                         | Service UUID                                                                                                  |
| signing_secret               | VARCHAR(128)                        | A previously agreed secret that is used to sign all requests sent to SiGa by this service                     |
| client_id                    | INTEGER                             | Client ID (foreign key to SIGA_CLIENT)                                                                        |
| name                         | VARCHAR(100)                        | Service name                                                                                                  |
| sk_relying_party_name        | VARCHAR(100)                        | [MID REST relying party name](https://github.com/SK-EID/MID#21-relyingpartyname)                              |
| sk_relying_party_uuid        | VARCHAR(100)                        | [MID REST relying party UUID](https://github.com/SK-EID/MID#22-relyingpartyuuid)                              |
| smart_id_relying_party_name  | VARCHAR(100)                        | [Smart-ID relying party name](https://github.com/SK-EID/smart-id-documentation#32-relyingpartyname-handling)  |
| smart_id_relying_party_uuid  | VARCHAR(100)                        | [Smart-ID relying party UUID](https://github.com/SK-EID/smart-id-documentation#31-uuid-encoding)              |
| billing_email                | VARCHAR(128)                        | (currently not used by SiGa)                                                                                  |
| max_connection_count         | INTEGER                             | Allowed maximum number of active sessions for this service. A value of `-1` indicates no limit                |
| max_connections_size         | BIGINT                              | Allowed cumulative maximum data volume* for all active sessions. A value of `-1` indicates no limit           |
| max_connection_size          | BIGINT                              | Allowed maximum data volume* for a single session. A value of `-1` indicates no limit                         |
| inactive                     | BOOLEAN                             | Indicates if the service is active or not                                                                     |
| created_at                   | TIMESTAMP                           | Service creation date                                                                                         |
| updated_at                   | TIMESTAMP                           | Service update date                                                                                           |

\* data volume is based on the content length of HTTP POST requests.

#### SIGA_CONNECTION

A table holding cumulative data volume* per active session.

| Column name   | Type                                | Description                                                                  |
| ------------- | ----------------------------------- | ---------------------------------------------------------------------------- |
| id            | SERIAL (autoincrement primary key)  | Entry ID                                                                     |
| container_id  | VARCHAR(36)                         | Container ID (an internal identifier identifying a currently active session) |
| service_id    | INTEGER                             | Service ID (foreign key to SIGA_SERVICE)                                     |
| size          | BIGINT                              | Cumulative data volume* for this session                                     |
| created_at    | TIMESTAMP                           | Connection creation date                                                     |
| updated_at    | TIMESTAMP                           | Connection update date                                                       |

\* data volume is based on the content length of HTTP POST requests.

#### SIGA_IP_PERMISSION

A table holding ip permissions for external Siga service (SOAP PROXY)

| Column name   | Type                                | Description                                           |
| ------------- | ----------------------------------- | ----------------------------------------------------- |
| id            | SERIAL (autoincrement primary key)  | Entry ID                                              |
| service_id    | INTEGER                             | Service ID (foreign key to SIGA_SERVICE)              |
| ip_address    | VARCHAR(36)                         | Allowed ip address                                    |
| created_at    | TIMESTAMP                           | Ip permission creation date                           |
| updated_at    | TIMESTAMP                           | Ip permission update date                             |

## Running SiGa with Docker

### For development and testing purposes only!

#### Preconditions
1. Java 17
2. Docker must be installed and running.
3. The [siga-demo-application](https://github.com/open-eid/SiGa-demo-application) docker image must be built and available on Docker as `siga-demo-application:latest`.

#### First time setup: 
1. Build this project
```bash
./mvnw clean install
```

2. Build SiGa webapp docker image 
```bash
./mvnw spring-boot:build-image -pl siga-webapp -DskipTests
```

3. Generate application keystores/truststores
```bash
./docker/tls/generate-certificates.sh
```

4. From your project directory, start up your applications in test mode by running
```bash
docker-compose up --build
```

Now SiGa itself is accessible https://localhost:8443/siga and siga-demo-application https://siga-demo.localhost:9443/ .
You can view the logs for all the running containers at http://localhost:11080 .

#### For updating software:

1. Build the project with changes
```bash
./mvnw clean install
```

2. Build SiGa webapp docker image
```bash
./mvnw spring-boot:build-image -pl siga-webapp -DskipTests
```

3. Run the image
```bash
docker-compose up
```

## Integration tests

Integration tests for SiGa are available in the following repository: https://github.com/open-eid/SiGa-Tests
