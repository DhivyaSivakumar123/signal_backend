FROM openjdk:21-slim
EXPOSE 8085
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
