# Stage 1: compile/package the Java application
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy pom first so Maven dependency downloads become a reusable Docker layer.
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: run only with a JRE. The Maven/JDK build toolchain is not in the final image.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/docker-k8s-lab-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 5000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
