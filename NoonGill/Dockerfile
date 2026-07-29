FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m

COPY src ./src
RUN ./gradlew bootJar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx512m

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S noongill && adduser -S noongill -G noongill
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER noongill
EXPOSE 8080
ENTRYPOINT ["java", "-Xms128m", "-Xmx512m", "-jar", "/app/app.jar"]
