FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/portfolio-service-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 4007
EXPOSE 9005
ENTRYPOINT ["java", "-jar", "app.jar"]

