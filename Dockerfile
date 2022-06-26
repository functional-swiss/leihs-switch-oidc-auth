ARG UBUNTU_VERSION=22.04

FROM ubuntu:${UBUNTU_VERSION}

ENV DEBIAN_FRONTEND noninteractive

RUN apt-get update && apt-get install -y pkg-config make gcc gdb lcov valgrind vim curl iputils-ping wget
RUN apt-get update && apt-get install -y autoconf automake libtool
RUN apt-get update && apt-get install -y libssl-dev libjansson-dev libcurl4-openssl-dev check
RUN apt-get update && apt-get install -y apache2 apache2-dev
RUN apt-get update && apt-get install -y libpcre2-dev zlib1g-dev
RUN apt-get update && apt-get install -y libapache2-mod-php libhiredis-dev
RUN apt-get update && apt-get install -y libcjose-dev

RUN a2enmod ssl
RUN a2ensite default-ssl

COPY mod_auth_openidc /root/mod_auth_openidc
WORKDIR /root/mod_auth_openidc

RUN ./autogen.sh
RUN ./configure CFLAGS="-g -O0" LDFLAGS="-lrt"
#-I/usr/include/apache2
RUN make clean && make check
RUN make install

WORKDIR /root

COPY proto-config/docker/openidc.conf /etc/apache2/conf-available/openidc.conf
COPY proto-config/docker/openidc_secret_vars.conf /etc/apache2/oidc_vars.conf

# COPY keygen/fun-leihs-test_public.pem /root/fun-leihs-test_public.pem
# COPY keygen/fun-leihs-test_private.pem /root/fun-leihs-test_private.pem

RUN mkdir -p /var/www/html/authenticators/switch-open-id/phbern/
COPY proto-config/docker/index.php /var/www/html/authenticators/switch-open-id/phbern/protected/index.php

RUN a2enconf openidc
RUN /usr/sbin/apache2ctl configtest
RUN /usr/sbin/apache2ctl start
