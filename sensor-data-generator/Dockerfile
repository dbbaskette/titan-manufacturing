FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/sensor-data-generator.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
