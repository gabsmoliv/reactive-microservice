##
# Build context is the repository root (the Maven parent POM is required).
#   docker build -f auth-service/Dockerfile -t auth-service .
##
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY auth-service/pom.xml auth-service/pom.xml
COPY task-service/pom.xml task-service/pom.xml
COPY karate-tests/pom.xml karate-tests/pom.xml
RUN mvn -B -q -pl auth-service -am dependency:go-offline

COPY auth-service/src auth-service/src
RUN mvn -B -q -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
