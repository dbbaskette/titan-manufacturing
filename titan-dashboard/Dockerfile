# ═══════════════════════════════════════════════════════════════════════════
# TITAN MANUFACTURING 5.0 — Dashboard Dockerfile
# Multi-stage build for production React app
# ═══════════════════════════════════════════════════════════════════════════

# Production stage — uses pre-built dist/ from local npm run build
FROM nginx:alpine

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy pre-built assets
COPY dist/ /usr/share/nginx/html

# Expose port
EXPOSE 3000

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
