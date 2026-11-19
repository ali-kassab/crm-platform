# syntax=docker/dockerfile:1
# Backend API image (R13). Build context: ./backend
#   docker build -t crm-backend ./backend
# Ubuntu-based (glibc) images: webp-imageio ships a glibc native library, so Alpine/musl is not used.

FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/crm-platform-backend-*.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:17-jre-noble
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# Spring Boot layers: dependencies change rarely, application code often
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
# No curl/wget in the JRE image: probe the liveness endpoint with bash's /dev/tcp
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health/liveness HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"UP\"" <&3'
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
