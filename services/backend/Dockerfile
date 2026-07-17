# syntax=docker/dockerfile:1.7
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY services/backend/ ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S hhy \
    && adduser -S -G hhy -u 10001 hhy
WORKDIR /app
COPY --from=build --chown=hhy:hhy /workspace/boot/target/hhy-backend.jar /app/hhy-backend.jar
USER 10001:10001
EXPOSE 8080
EXPOSE 9091
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
  CMD curl -fsS http://127.0.0.1:9091/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-XX:+ExitOnOutOfMemoryError","-jar","/app/hhy-backend.jar"]
