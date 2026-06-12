# EBZ Controlling — Integration (Quarkus, JVM-Modus). Multi-Stage: Build mit Maven+JDK 21,
# Laufzeit auf schlankem JRE 21. native-image bewusst optional (Plan §9 L30).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
# Erst pom für Dependency-Layer-Cache, dann Sourcen.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Quarkus fast-jar Layout
COPY --from=build /build/target/quarkus-app/lib/ ./lib/
COPY --from=build /build/target/quarkus-app/*.jar ./
COPY --from=build /build/target/quarkus-app/app/ ./app/
COPY --from=build /build/target/quarkus-app/quarkus/ ./quarkus/
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
