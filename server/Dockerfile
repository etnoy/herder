FROM openjdk:11
ENV APP_HOME=/usr/app/
WORKDIR /usr/app/
COPY build/libs/*jar herder-backend.jar
EXPOSE 8080
CMD ["java", "-jar", "herder-backend.jar", "--spring.data.mongodb.uri=mongodb://mongodb:27017/herder"]