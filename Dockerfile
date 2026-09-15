FROM maven:3.9-eclipse-temurin-8 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:8-jre

WORKDIR /app

RUN groupadd --system --gid 10001 cmdb \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin cmdb

COPY --from=build --chown=10001:10001 /workspace/target/cmdb-1.0.0.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    TZ=Asia/Shanghai

USER 10001:10001
EXPOSE 8080

STOPSIGNAL SIGTERM

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
