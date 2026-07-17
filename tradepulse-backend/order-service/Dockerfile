FROM eclipse-temurin:21-jdk AS runner

WORKDIR /app

COPY target/order-service-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 4006

ENTRYPOINT ["java", "-jar", "app.jar"]

