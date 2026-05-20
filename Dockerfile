# syntax=docker/dockerfile:1.7

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

# Copy gradle wrapper + build metadata first for layer caching
COPY gradlew gradlew
COPY gradle/wrapper gradle/wrapper
COPY build.gradle settings.gradle gradle.properties ./

# Pre-fetch dependencies (warms cache; fails fast on upstream outage)
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy sources and build
COPY src src
# CI runs spotless+tests; container build is bin-packing only
RUN ./gradlew --no-daemon clean bootJar -x test -x spotlessCheck

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
