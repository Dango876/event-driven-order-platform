# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -U -pl services/notification-service -am clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/services/notification-service/target/notification-service-1.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8087
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
