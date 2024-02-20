FROM eclipse-temurin:17.0.10_7-jre-jammy

ADD /build/libs/cetrack-*.jar /app/service.jar
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=mysql

CMD ["java", "-jar", "service.jar"]
