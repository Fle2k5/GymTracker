FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 gymtracker
COPY --from=build /workspace/target/gym-tracker-web-3.0.0.jar app.jar
USER gymtracker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
