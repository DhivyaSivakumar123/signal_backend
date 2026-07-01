FROM openjdk:21-slim
EXPOSE 8084
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
