FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests -Dmaven.javadoc.skip=true -Dgpg.skip=true install
COPY api ./api
RUN mvn -B -ntp -f api/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/images && chown -R app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/api/target/sourceafis-api.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
