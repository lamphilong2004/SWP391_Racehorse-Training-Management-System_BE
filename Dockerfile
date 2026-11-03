FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
# Verify only the deployment surface; previously accepted business gates run separately.
RUN mvn -B -ntp -Dtest=StagingDeploymentTest,RenderTailscaleTest,LoopbackTcpBridgeTest package

# Official userspace binaries; no TUN, privileged container, routing or Funnel.
FROM tailscale/tailscale:v1.104.1 AS tailscale

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:1000 /build/target/racehorse-backend-0.1.0-SNAPSHOT.jar /app/app.jar
COPY --from=tailscale /usr/local/bin/tailscale /usr/local/bin/tailscaled /usr/local/bin/
USER 10001:1000
ENV SPRING_PROFILES_ACTIVE=staging
EXPOSE 10000
ENTRYPOINT ["java", "-Xms32m", "-Xmx192m", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=32m", "-XX:MaxDirectMemorySize=32m", "-XX:+ExitOnOutOfMemoryError", "-Dloader.main=com.racehorse.staging.network.RenderTailscaleLauncher", "-cp", "/app/app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"]
