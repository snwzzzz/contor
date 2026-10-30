# contor-api for the personal production instance (built by scripts/prod.sh from a clean export of main).
# Build context: the repository root (the backend generates its API from api-spec/openapi.yaml).

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY api-spec api-spec
COPY contor-api contor-api
WORKDIR /src/contor-api
# The Maven cache survives between builds, so an update only downloads what changed. Tests run in the dev checkout, not here.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests && cp target/contor-api-*.jar /src/app.jar

FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --no-create-home contor
COPY --from=build /src/app.jar /app/app.jar
USER contor
WORKDIR /app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
