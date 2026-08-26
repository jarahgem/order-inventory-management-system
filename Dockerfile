FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S application && adduser -S application -G application
COPY --from=build /workspace/target/order-inventory-service.jar /app/order-inventory-service.jar
RUN chown -R application:application /app

USER application
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/order-inventory-service.jar"]
