# Frontend Next.js (frontend/web). Build context la ./frontend nen moi duong dan deu
# bat dau bang web/.
FROM node:22-alpine AS deps
WORKDIR /app
COPY web/package.json web/package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY web/ ./
# QUAN TRONG: voi output "standalone", Next tinh rewrites() LUC BUILD roi nhung ket qua
# vao manifest — no KHONG doc lai bien moi truong luc server khoi dong. Khong truyen bien
# o day thi next.config.ts roi ve mac dinh http://localhost:8080, tuc la localhost BEN
# TRONG container, va moi request /api/* se ECONNREFUSED.
ARG API_GATEWAY_URL=http://api-gateway:8080
ENV API_GATEWAY_URL=$API_GATEWAY_URL
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
# Server standalone doc PORT va HOSTNAME; phai nghe 0.0.0.0 thi ngoai container moi vao duoc.
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# output: "standalone" gom san server.js kem dung dependency can thiet.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
