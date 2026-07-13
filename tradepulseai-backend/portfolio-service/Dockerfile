FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=builder /app/target/portfolio-service-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 4007
EXPOSE 9005
ENTRYPOINT ["java", "-jar", "app.jar"]

