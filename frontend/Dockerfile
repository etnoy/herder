# Stage 1
FROM node:16-alpine as build
WORKDIR /dist/src/app

# Run command in Virtual directory
RUN npm cache clean --force
# Copy files from local machine to virtual directory in docker image
COPY . .
RUN npm install
RUN npm run build --prod

### STAGE 2:RUN ###
# Defining nginx image to be used
FROM nginx:1.23 AS ngi
# Copying compiled code and nginx config to different folder
# NOTE: This path may change according to your project's output folder 
COPY --from=build /dist/src/app/dist/herder /usr/share/nginx/html
COPY /nginx.conf  /etc/nginx/conf.d/default.conf
# Exposing a port, here it means that inside the container 
# the app will be using Port 80 while running
EXPOSE 80