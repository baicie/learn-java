FROM eclipse-temurin:17-jre

WORKDIR /app

COPY apps/demo-order-service/target/demo-order-service-0.1.0-SNAPSHOT.jar /app/app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
