# Build stage: Maven + JDK compile the Java application
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy pom first so dependency downloads can be cached
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage: smaller JRE-only image
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/docker-k8s-lab-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 5000

HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
  CMD sh -c 'exec 3<>/dev/tcp/127.0.0.1/5000 && printf "GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n" >&3 && grep -q "200" <&3' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
