FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
WORKDIR /build
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci
COPY next.config.mjs eslint.config.mjs postcss.config.mjs jsconfig.json ./
COPY src ./src
COPY public ./public
# Public browser setting only. Server proxy credentials are runtime-only.
ARG NEXT_PUBLIC_API_BASE_URL=/api/proxy
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN npm run lint && npm run build

FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS runtime
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="savix-frontend" \
      org.opencontainers.image.revision=$VCS_REF
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
RUN addgroup -S -g 1001 nextjs && adduser -S -D -H -u 1001 -G nextjs nextjs
WORKDIR /app
COPY --from=build --chown=nextjs:nextjs /build/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /build/.next/static ./.next/static
COPY --from=build --chown=nextjs:nextjs /build/public ./public
USER nextjs:nextjs
EXPOSE 3000
CMD ["node", "server.js"]
