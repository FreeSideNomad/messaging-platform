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

# Copy production sources/resources only (tests stay out of the build context)
COPY msg-platform-core/src/main msg-platform-core/src/main
COPY msg-platform-persistence-jdbc/src/main msg-platform-persistence-jdbc/src/main
COPY msg-platform-messaging-ibmmq/src/main msg-platform-messaging-ibmmq/src/main
COPY msg-platform-events-kafka/src/main msg-platform-events-kafka/src/main
COPY msg-platform-processor/src/main msg-platform-processor/src/main
COPY msg-platform-api/src/main msg-platform-api/src/main

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
