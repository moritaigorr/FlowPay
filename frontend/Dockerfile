FROM node:24.18.0-alpine AS build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.30.6-alpine
ENV NGINX_ENVSUBST_FILTER=^FLOWPAY_
COPY infra/nginx/default.conf /etc/nginx/templates/default.conf.template
COPY infra/nginx/proxy-api.conf /etc/nginx/proxy-api.conf
COPY --from=build /build/frontend/dist/ /usr/share/nginx/html/
EXPOSE 80
