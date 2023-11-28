FROM ubuntu:22.04

WORKDIR /app
RUN apt-get update --yes && DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install wget postgresql ca-certificates  --yes --no-install-recommends

RUN apt-get install xz-utils --yes --no-install-recommends

RUN wget -q https://db.in.tum.de/~neumann/umbra.tar.xz && ls && tar xvf umbra.tar.xz && rm umbra.tar.xz

EXPOSE 5432

ENV PATH /app/umbra/bin:${PATH}

COPY docker-entrypoint.sh /start.sh
COPY init.sql /app/init.sql

ENTRYPOINT [ "/start.sh" ]