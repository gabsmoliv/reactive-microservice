##
# Build context is the repository root (the Maven parent POM is required).
#   docker build -f task-service/Dockerfile -t task-service .
##
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY auth-service/pom.xml auth-service/pom.xml
COPY task-service/pom.xml task-service/pom.xml
COPY karate-tests/pom.xml karate-tests/pom.xml
RUN mvn -B -q -pl task-service -am dependency:go-offline

COPY task-service/src task-service/src
RUN mvn -B -q -pl task-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/task-service/target/task-service-*.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
