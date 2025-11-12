FROM eclipse-temurin:21-jre
FROM openjdk:8-jre-alpine
WORKDIR /
COPY target/*-SNAPSHOT.jar /app.jar
EXPOSE 8089
ENTRYPOINT ["java","-jar","/app.jar"]
