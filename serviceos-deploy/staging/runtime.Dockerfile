# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

ARG VCS_REF=unknown
ARG BUILD_CREATED=unknown
LABEL org.opencontainers.image.title="ServiceOS Backend Rollback Candidate" \
      org.opencontainers.image.source="https://github.com/hctwgl/serviceos" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_CREATED}"

RUN addgroup -S -g 10001 serviceos \
    && adduser -S -D -H -u 10001 -G serviceos serviceos
WORKDIR /opt/serviceos
COPY --chown=10001:10001 app.jar /opt/serviceos/app.jar
COPY --chown=10001:10001 --chmod=0555 entrypoint.sh /opt/serviceos/bin/entrypoint.sh
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp" TZ=UTC
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["/opt/serviceos/bin/entrypoint.sh"]
CMD ["server"]
