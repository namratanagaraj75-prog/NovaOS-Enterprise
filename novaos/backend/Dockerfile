FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system novaos \
    && useradd --system --gid novaos --home-dir /app --shell /usr/sbin/nologin novaos

COPY --from=build --chown=novaos:novaos /app/target/api-0.0.1-SNAPSHOT.jar /app/app.jar

USER novaos

EXPOSE 10000

CMD ["java", "-jar", "/app/app.jar"]
