FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jdk AS runner

WORKDIR /app

COPY --from=builder /app/target/stock-service-0.0.1-SNAPSHOT.jar ./app.jar

EXPOSE 4003

ENTRYPOINT ["java", "-jar", "app.jar"]

