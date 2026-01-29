FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/camera-relay-server.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
