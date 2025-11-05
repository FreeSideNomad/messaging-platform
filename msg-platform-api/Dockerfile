# Multi-stage build for msg-platform-api
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy all pom files
COPY pom.xml .
COPY msg-platform-core/pom.xml msg-platform-core/
COPY msg-platform-persistence-jdbc/pom.xml msg-platform-persistence-jdbc/
COPY msg-platform-messaging-ibmmq/pom.xml msg-platform-messaging-ibmmq/
COPY msg-platform-events-kafka/pom.xml msg-platform-events-kafka/
COPY msg-platform-processor/pom.xml msg-platform-processor/
COPY msg-platform-api/pom.xml msg-platform-api/
COPY msg-platform-worker/pom.xml msg-platform-worker/
COPY msg-platform-payments-worker/pom.xml msg-platform-payments-worker/

# Copy source code and resources for dependencies
COPY msg-platform-core/src msg-platform-core/src
COPY msg-platform-persistence-jdbc/src msg-platform-persistence-jdbc/src
COPY msg-platform-persistence-jdbc/src/main/resources msg-platform-persistence-jdbc/src/main/resources
COPY msg-platform-messaging-ibmmq/src msg-platform-messaging-ibmmq/src
COPY msg-platform-events-kafka/src msg-platform-events-kafka/src
COPY msg-platform-processor/src msg-platform-processor/src
COPY msg-platform-api/src msg-platform-api/src

# Build only API module (skip tests completely)
RUN mvn clean package -DskipTests -Dmaven.test.skip=true -pl msg-platform-api -am

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy JAR
COPY --from=build /app/msg-platform-api/target/msg-platform-api-*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run with JAVA_OPTS support
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
