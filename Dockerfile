FROM maven:3.9.16-eclipse-temurin-17-alpine AS builder

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-ubi9-minimal AS runtime

LABEL org.opencontainers.image.title="FinGuard Core" \
      org.opencontainers.image.description="Transaction import, reconciliation, and exception review service"

RUN useradd --uid 10001 \
        --user-group \
        --home-dir /opt/finguard \
        --shell /sbin/nologin \
        finguard \
    && mkdir -p /opt/finguard \
    && chown -R finguard:finguard /opt/finguard

WORKDIR /opt/finguard

COPY --from=builder --chown=finguard:finguard /workspace/target/finguard-core-*.jar ./app.jar

USER finguard

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=12 \
    CMD wget --quiet --tries=1 --output-document=/dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/opt/finguard/app.jar"]
