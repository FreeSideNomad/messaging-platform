# Multi-stage build for msg-platform-worker
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy all pom files
COPY pom.xml .
COPY msg-platform-core/pom.xml msg-platform-core/
COPY msg-platform-messaging-ibmmq/pom.xml msg-platform-messaging-ibmmq/
COPY msg-platform-events-kafka/pom.xml msg-platform-events-kafka/
COPY msg-platform-api/pom.xml msg-platform-api/
COPY msg-platform-worker/pom.xml msg-platform-worker/

# Copy source code for dependencies
COPY msg-platform-core/src msg-platform-core/src
COPY msg-platform-messaging-ibmmq/src msg-platform-messaging-ibmmq/src
COPY msg-platform-events-kafka/src msg-platform-events-kafka/src
COPY msg-platform-worker/src msg-platform-worker/src

# Build only Worker module
RUN mvn clean package -DskipTests -pl msg-platform-worker -am

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy JAR
COPY --from=build /app/msg-platform-worker/target/msg-platform-worker-*.jar app.jar

# Expose port (variable per worker)
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${WORKER_PORT:-9090}/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
