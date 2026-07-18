FROM node:24-alpine AS build

WORKDIR /app
COPY serviceos-network-web/package.json serviceos-network-web/package-lock.json ./
COPY serviceos-web-core /serviceos-web-core
RUN npm ci --ignore-scripts --no-audit --no-fund

COPY serviceos-network-web .
ARG VITE_SERVICEOS_API_BASE_URL=/api/v1
ARG VITE_SERVICEOS_CLIENT_VERSION
ARG VITE_OIDC_ENABLED=false
ARG VITE_OIDC_ISSUER
ARG VITE_OIDC_CLIENT_ID
ENV VITE_SERVICEOS_API_BASE_URL=${VITE_SERVICEOS_API_BASE_URL} \
    VITE_SERVICEOS_CLIENT_VERSION=${VITE_SERVICEOS_CLIENT_VERSION} \
    VITE_OIDC_ENABLED=${VITE_OIDC_ENABLED} \
    VITE_OIDC_ISSUER=${VITE_OIDC_ISSUER} \
    VITE_OIDC_CLIENT_ID=${VITE_OIDC_CLIENT_ID}
RUN test -n "${VITE_SERVICEOS_CLIENT_VERSION}" && npm run build

FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY serviceos-network-web/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV SERVICEOS_API_UPSTREAM=http://127.0.0.1:8080
EXPOSE 8080
