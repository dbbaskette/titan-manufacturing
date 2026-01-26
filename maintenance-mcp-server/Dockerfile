FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app

COPY target/maintenance-mcp-server.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
