FROM node:22-alpine AS builder
WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
ARG VITE_NAVER_MAP_CLIENT_ID
ENV VITE_NAVER_MAP_CLIENT_ID=${VITE_NAVER_MAP_CLIENT_ID}
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/dist /usr/share/nginx/html
EXPOSE 80
