FROM eclipse-temurin:21
LABEL maintainer="https://www.canner.io/"
WORKDIR /usr/src/app

RUN \
    apt update && \
    apt -y install curl gpg lsb-release && \
    curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | gpg --dearmor -o /etc/apt/trusted.gpg.d/postgresql.gpg && \
    echo "deb http://apt.postgresql.org/pub/repos/apt/ `lsb_release -cs`-pgdg main" | tee /etc/apt/sources.list.d/pgdg.list && \
    apt update && \
    apt -y install postgresql-client-13

ARG ANALYTICS_VERSION
ENV ENV_ANALYTICS_VERSION=${ANALYTICS_VERSION}
ENV ANALYTICS_JAR=analytics-server-${ENV_ANALYTICS_VERSION}-executable.jar
COPY ${ANALYTICS_JAR} ./

COPY entrypoint.sh ./
RUN chmod +x ./entrypoint.sh

CMD ./entrypoint.sh ${ANALYTICS_JAR} ${MAX_HEAP_SIZE} ${MIN_HEAP_SIZE}
