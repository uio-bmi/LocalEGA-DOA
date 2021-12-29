FROM maven:3.8.4-openjdk-17-slim as builder

COPY pom.xml .

RUN mvn dependency:go-offline --no-transfer-progress

COPY src/ /src/

RUN mvn clean install -DskipTests --no-transfer-progress

FROM openjdk:17-alpine

RUN apk add --no-cache ca-certificates openssl

COPY --from=builder /target/localega-doa-*.jar /localega-doa.jar

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh

USER 65534

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

CMD ["java", "-jar", "/localega-doa.jar"]
