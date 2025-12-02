# Legacy Analytics Core
It's the legacy version of the Analytics core implemented in Java. This module is utilized by the API v2 of the ibis-server for SQL planning and is responsible for building the `analytics-engine` Docker image.
Currently, Analytics engine has been migrated to [a new Rust implementation](../analytics-core/) 🚀.

## Requirements
- JDK 21+

## Running Analytics Core Server
We recommend running Analytics core server using Docker. It's the easiest way to start the analytics core server:
```
docker run --name java-engine -p 8080:8080 -v $(pwd)/docker/etc:/usr/src/app/etc ghcr.io/canner/analytics-engine:latest  
```

### Maven Build
For developing, you can build Analytics core by yourself. Analytics core is a standard maven project. We can build an executable jar using the following command:
```
./mvnw clean install -DskipTests -Dair.check.skip-dependency=true -P exec-jar
```
Then, start Analytics core server
```
java -Dconfig=docker/etc/config.properties --add-opens=java.base/java.nio=ALL-UNNAMED -jar analytics-server/target/analytics-server-0.15.2-SNAPSHOT-executable.jar
```

### Running Analytics Engine in IDE
After building with Maven, you can run the project in your IDE. We recommend using [IntelliJ IDEA](http://www.jetbrains.com/idea/). Since Analytics core is a standard Maven project, you can easily import it into your IDE. In IntelliJ, choose `Open Project from the Quick Start` box or select `Open` from the File menu and choose the root `pom.xml` file.

After opening the project in IntelliJ, ensure that the Java SDK is properly configured for the project:

1. Open the File menu and select **Project Structure**.
2. In the **SDKs** section, ensure that JDK 21 is selected (create one if it does not exist).
3. In the **Project** section, ensure the Project language level is set to 21.

Set up the running profile with the following configuration:
- **SDK**: The JDK you configured.
- **Main class**: `io.analytics.server.AnalyticsServer`
- **VM options**: `-Dconfig=docker/etc/config.properties`
- **Working directory**: The path to `analytics-core-legacy`.