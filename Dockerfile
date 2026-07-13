FROM eclipse-temurin:25-jre-alpine

COPY /build/libs/cetrack-*.jar /app/service.jar
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=postgres

CMD ["java", "-jar", "service.jar"]
