FROM eclipse-temurin:21-jre

WORKDIR /app

COPY worker/build/libs/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
