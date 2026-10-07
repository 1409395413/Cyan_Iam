# ---------- stage 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
# 先解析依赖，利用 Docker 层缓存
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- stage 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata wget \
    && addgroup -S app && adduser -S -G app app
COPY --from=build /build/target/portfolio-server.jar /app/app.jar
RUN mkdir -p /var/lib/portfolio/upload && chown -R app:app /app /var/lib/portfolio
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70"
HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
