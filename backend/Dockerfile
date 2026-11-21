FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 apt-get update -qq && apt-get install -y -qq maven >/dev/null && mvn -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home podcastai
USER podcastai
WORKDIR /app
COPY --from=build /src/target/podcastai-backend-*.jar app.jar
ENV STORAGE_DIR=/app/data/audio
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
