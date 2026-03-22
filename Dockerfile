FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew ./gradlew

RUN chmod +x gradlew

COPY build.gradle settings.gradle ./

COPY app ./app
COPY modules ./modules
COPY shared ./shared

RUN ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/app/build/libs/app.jar /app/app.jar

USER appuser

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

