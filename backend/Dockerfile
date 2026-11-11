FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY backend/src ./src
COPY docs/api/openapi.yaml /build/docs/api/openapi.yaml
RUN mvn -B --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S -g 10001 flowpay && adduser -S -u 10001 -G flowpay flowpay
WORKDIR /app
COPY --from=build --chown=flowpay:flowpay /build/backend/target/flowpay-0.1.0-SNAPSHOT.jar ./flowpay.jar
USER flowpay
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/flowpay.jar"]
