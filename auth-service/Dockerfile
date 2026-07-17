FROM eclipse-temurin:21-jre
EXPOSE 8081
COPY target/auth-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:gcp}", "-jar", "/app.jar"]
