FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew gradle/wrapper/bootstrap-wrapper.sh \
    && ./gradlew --no-daemon dependencies

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S application && adduser -S application -G application
COPY --from=build /workspace/build/libs/order-inventory-service.jar /app/order-inventory-service.jar
RUN chown -R application:application /app

USER application
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/order-inventory-service.jar"]
