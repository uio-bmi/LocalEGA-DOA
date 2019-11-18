FROM openjdk:13-alpine

COPY /target/*-SNAPSHOT.jar /localega-doa.jar

CMD ["java", "-jar", "/localega-doa.jar"]
