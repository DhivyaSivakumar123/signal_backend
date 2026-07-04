FROM eclipse-temurin:21-jre-slim
EXPOSE 8080
COPY target/gateway-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
