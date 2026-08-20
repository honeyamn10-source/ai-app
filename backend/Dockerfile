FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json ./
COPY backend ./backend
RUN mkdir -p /app/backend/data && chown -R node:node /app
USER node
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -q -O- http://127.0.0.1:8787/health || exit 1
CMD ["node", "backend/src/server.mjs"]

