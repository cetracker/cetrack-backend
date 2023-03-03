#FROM eclipse-temurin:17.0.6_10-jre-jammy
FROM eclipse-temurin:17.0.6_10-jre-alpine

# RUN useradd -ms /bin/bash -u 1000 app
# ruUSER app

#ENTRYPOINT [ "/opt/bin/entrypoint.sh" ]

ADD cetrack-*.jar /app/service.jar
WORKDIR /app

CMD ["java", "-jar", "-Dspring.profiles.active=default, h2db", "service.jar"]

# java
# #!/bin/sh
# exec ${JAVA_HOME}/bin/java "$@"
