FROM nginx:alpine

# Remove default nginx configuration
RUN rm -rf /etc/nginx/conf.d/default.conf

# Copy custom reverse-proxy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy frontend static files
COPY index.html style.css app.js /usr/share/nginx/html/

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
