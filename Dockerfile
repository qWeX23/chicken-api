# --- Build stage ---
FROM eclipse-temurin:21-jdk-alpine as build
WORKDIR /app
COPY . .
COPY dummy-creds.json /app/dummy-creds.json
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/dummy-creds.json
RUN apk add --no-cache bash git \
 && chmod +x gradlew \
 && ./gradlew build -x test --no-daemon
#TODO get this to run the tests correctly
# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
LABEL authors="qWeX23"
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
