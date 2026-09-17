# Build with the repository's Maven 3.9.10 wrapper on Java 21.
FROM eclipse-temurin:21-jdk-alpine@sha256:6ea5548706b60ac0a602eaf48af74792cbab012d90e811ca8db6184b16b5c3d6 AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src ./src
# Keep tests in the image build gate; only the executable JAR reaches runtime.
RUN ./mvnw -B -ntp clean verify

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699 AS runtime
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="savix-backend" \
      org.opencontainers.image.revision=$VCS_REF
RUN addgroup -S -g 10001 spring && adduser -S -D -H -u 10001 -G spring spring
WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/backend-0.0.1-SNAPSHOT.jar ./app.jar
USER spring:spring
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
