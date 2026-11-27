# Build context: repository root (docker-compose.yml), so database/migrations is available.
# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/backend
# Copy the wrapper and pom first so dependencies are cached between builds
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY database/migrations /app/database/migrations
COPY backend/src src
RUN ./mvnw -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/receipts /data/exports && chown -R app:app /data
COPY --from=build /app/backend/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    RECEIPTS_DIR=/data/receipts \
    EXPORTS_DIR=/data/exports
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
