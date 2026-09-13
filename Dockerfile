FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /code

# Continuous-profiling agent, fetched before the source copy so it stays cached
# across code changes. The checksum is the one Maven Central publishes for this
# exact artifact; a mismatch fails the build rather than shipping an unknown jar.
ARG PYROSCOPE_AGENT_VERSION=2.1.2
ARG PYROSCOPE_AGENT_SHA1=173c22f266ce897566a4e0bb5a05f6c3c22bd818
RUN wget -q -O /pyroscope.jar \
      "https://repo1.maven.org/maven2/io/pyroscope/agent/${PYROSCOPE_AGENT_VERSION}/agent-${PYROSCOPE_AGENT_VERSION}.jar" \
 && echo "${PYROSCOPE_AGENT_SHA1}  /pyroscope.jar" | sha1sum -c -

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
# is eclipse-temurin (glibc). This libc mismatch is intentional and correct: only JARs cross the
# stage boundary (COPY --from=build below), and a JAR is libc- and architecture-neutral bytecode.
# The Pyroscope agent jar does carry native async-profiler libraries, but it selects and extracts
# the one matching the *runtime* platform, so it binds to this stage's glibc, not the build stage's
# musl. Do not "harmonise" the two stages onto the same libc.
FROM eclipse-temurin:21.0.12_8-jre-noble

WORKDIR /app
# Copy the jar to the production image from the builder stage.
COPY --from=build /code/build/libs/spring-modulith-kotlin-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /pyroscope.jar pyroscope.jar

# Profiling is off unless a Pyroscope server is configured, so that running this
# image on its own does not emit connection errors. Compose turns it on.
ENV PYROSCOPE_AGENT_ENABLED=false
ENV PYROSCOPE_APPLICATION_NAME=spring-modulith-kotlin
ENV PYROSCOPE_FORMAT=jfr
# itimer needs no perf_event access, which containers do not grant by default.
ENV PYROSCOPE_PROFILER_EVENT=itimer

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
ENTRYPOINT ["java","-javaagent:/app/pyroscope.jar","-jar","app.jar"]
