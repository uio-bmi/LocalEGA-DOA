FROM maven:3.6.0-jdk-13-alpine as builder

COPY . .

RUN mvn clean install -DskipTests=true -Dmaven.javadoc.skip=true -B -V -DskipDockerPush -DskipDockerBuild

FROM openjdk:13-alpine

COPY --from=builder /target/*-SNAPSHOT.jar /localega-doa.jar

COPY entrypoint.sh .

RUN chmod +x entrypoint.sh

#USER 1000

ENTRYPOINT ["/entrypoint.sh"]

CMD ["java", "-jar", "/localega-doa.jar"]
