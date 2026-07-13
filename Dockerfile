# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

# 多架构索引 digest 固定到 2026-06-22 发布的 Temurin 21.0.11+10；升级必须重新跑镜像验收。
FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY serviceos-backend/pom.xml serviceos-backend/pom.xml
COPY serviceos-contracts/pom.xml serviceos-contracts/pom.xml
COPY serviceos-backend/src/ serviceos-backend/src/

# 测试由前置 CI 执行；镜像阶段只从同一源码树生成可执行产物，避免把本机 target 带入镜像。
RUN ./mvnw --batch-mode --no-transfer-progress -pl serviceos-backend -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c AS runtime

ARG VCS_REF=unknown
ARG BUILD_CREATED=unknown
LABEL org.opencontainers.image.title="ServiceOS Backend" \
      org.opencontainers.image.source="https://github.com/hctwgl/serviceos" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_CREATED}"

RUN addgroup -S -g 10001 serviceos \
    && adduser -S -D -H -u 10001 -G serviceos serviceos

WORKDIR /opt/serviceos
COPY --from=build --chown=10001:10001 /workspace/serviceos-backend/target/serviceos-backend-*.jar app.jar
COPY --chown=10001:10001 --chmod=0555 serviceos-deploy/staging/entrypoint.sh /opt/serviceos/bin/entrypoint.sh

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp" \
    TZ=UTC

EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/opt/serviceos/bin/entrypoint.sh"]
CMD ["server"]
