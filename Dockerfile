FROM eclipse-temurin:17.0.6_10-jre-alpine

ADD /build/libs/cetrack-*.jar /app/service.jar
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=mysql

CMD ["java", "-jar", "service.jar"]
