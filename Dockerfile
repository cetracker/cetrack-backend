FROM eclipse-temurin:25-jre-noble

COPY /build/libs/cetrack-*.jar /app/service.jar
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=mysql

CMD ["java", "-jar", "service.jar"]
