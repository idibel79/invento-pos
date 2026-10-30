# syntax=docker/dockerfile:1
# Image unique de l'application : API Spring Boot qui sert aussi le build React.
# Multi-architecture : linux/amd64 (Cloud Run, PC) et linux/arm64 (Raspberry Pi).

# Le build du front ne dépend pas de l'architecture cible : il tourne sur celle de la machine.
FROM --platform=$BUILDPLATFORM node:22-alpine AS front
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=front /src/frontend/dist src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Djacoco.skip=true \
    && java -Djarmode=tools -jar target/invento-pos.jar extract --layers --launcher --destination /app

FROM eclipse-temurin:21-jre
RUN groupadd --system pos && useradd --system --gid pos --home-dir /app pos
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER pos
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
