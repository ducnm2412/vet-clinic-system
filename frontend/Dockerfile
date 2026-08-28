# Test UI tam thoi (frontend/test-ui): server Node thuan, khong co package.json,
# khong phu thuoc npm package nao -> chi can copy source va chay.
# Khi thay bang React/Next.js that thi viet lai Dockerfile nay theo chuan node build.
FROM node:22-alpine
WORKDIR /app
COPY test-ui ./
EXPOSE 3000
CMD ["node", "server.js"]
