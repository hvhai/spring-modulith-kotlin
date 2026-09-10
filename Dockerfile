# Use the official maven/Java 8 image to create a build artifact.
# https://hub.docker.com/_/maven
FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /code
#
# # Copy local code to the container image.
COPY . .
#
# # Build a release artifact.
RUN gradle clean build --no-daemon -x test


#
# Package stage
#
# https://docs.docker.com/develop/develop-images/multistage-build/#use-multi-stage-builds
#
# NOTE: the build stage above is gradle:8.11-jdk21-alpine (musl libc) while this runtime stage
# is eclipse-temurin (glibc). This libc mismatch is intentional and correct: only the fat JAR
# crosses the stage boundary (COPY --from=build below), and a JAR is libc- and
# architecture-neutral bytecode. There is no JNI and no native-image artifact carried over.
# Do not "harmonise" the two stages onto the same libc.
FROM eclipse-temurin:21.0.12_8-jre-noble

WORKDIR /app
# Copy the jar to the production image from the builder stage.
COPY --from=build /code/build/libs/spring-modulith-kotlin-0.0.1-SNAPSHOT.jar app.jar

# ENV PORT=8080
ARG APP_METHOD_API_TOKEN
ENV APP_METHOD_API_TOKEN $APP_METHOD_API_TOKEN

ARG DOMAIN
ENV DOMAIN $DOMAIN

ARG CLIENT_ID
ENV CLIENT_ID $CLIENT_ID

ARG CLIENT_SECRET
ENV CLIENT_SECRET $CLIENT_SECRET

EXPOSE 8080

# Run the web service on container startup.
ENTRYPOINT ["java","-jar","app.jar"]
