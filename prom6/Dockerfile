#
# Image for ProM 6
#

FROM zhicwu/prom:base

LABEL maintainer="zhicwu@gmail.com"

ENV PROM_MAJOR_VERSION=6 PROM_MINOR_VERSION=8 JMX_EXPORTER_VERSION=0.3.1 \
    PROM_BASE_URL=http://www.promtools.org MAVEN_BASE_URL=https://repo1.maven.org/maven2
ENV PROM_VERSION=$PROM_MAJOR_VERSION.$PROM_MINOR_VERSION \
    PROM_SCRIPT=ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}.sh \
    PROM_CLI_SCRIPT=ProM${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}CLI.sh

RUN wget --progress=dot:giga ${PROM_BASE_URL}/prom${PROM_MAJOR_VERSION}/downloads/prom-${PROM_VERSION}-all-platforms.tar.gz \
        ${MAVEN_BASE_URL}/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar \
        ${PROM_BASE_URL}/prom${PROM_MAJOR_VERSION}/downloads/example-logs.zip \
    && tar zxf *.tar.gz \
    && unzip *.zip \
    && rm -f *.tar.gz *.zip \
    && sed -i -e "s|\(^PROM_VERSION[[:space:]]*=\).*|\1 $PROM_VERSION|" \
        -e "s|\(^PACKAGE_URL[[:space:]]*=\).*|\1 ${PROM_BASE_URL}/prom${PROM_MAJOR_VERSION}/packages${PROM_MAJOR_VERSION}${PROM_MINOR_VERSION}/packages.xml|" \
        -e "s|\(^CONNECT_TIMEOUT[[:space:]]*=\).*|\1 10000|" \
        -e "s|\(^READ_TIMEOUT[[:space:]]*=\).*|\1 30000|" ProM.ini \
    && find . -type d | xargs chmod 755 \
    && find . -type f | xargs chmod 644 \
    && chmod +x *.sh

COPY content ./

RUN rm -f *.bat \
    && sed -i -e 's| -XX:MaxPermSize=256m||' \
        -e "s|\-Xmx4G|-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap \
             -javaagent:./jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar=1234:config.yaml \
             -XX:OnOutOfMemoryError=/usr/bin/oom_killer|" *.* \
    && cp $PROM_SCRIPT $PROM_CLI_SCRIPT \
    && sed -i -e 's|^\(MAIN=\).*|\1jdk.nashorn.tools.Shell\n[ ! -d logs ] \&\& mkdir logs|' \
        -e "s|\(.*\) \(\${MAIN}\)$|\1 \${JAVA_OPTS} \2 -fv -strict --language=es6 \$@|" \
        $PROM_CLI_SCRIPT \
    && ./docker-entrypoint.sh install.js

ENTRYPOINT ["./docker-entrypoint.sh"]

EXPOSE 1234 5900
