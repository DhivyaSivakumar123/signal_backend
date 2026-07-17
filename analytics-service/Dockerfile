FROM eclipse-temurin:21-jre
EXPOSE 8085
COPY target/analytics-service-1.0.0.jar app.jar
ENTRYPOINT ["java", "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:gcp}", "-jar", "/app.jar"]
