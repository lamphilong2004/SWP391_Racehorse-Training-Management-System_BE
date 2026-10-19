FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Verify only the deployment surface; previously accepted business gates run separately.
RUN mvn -B -ntp -Dtest=StagingDeploymentTest package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:1000 /build/target/racehorse-backend-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001:1000
ENV SPRING_PROFILES_ACTIVE=staging
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
