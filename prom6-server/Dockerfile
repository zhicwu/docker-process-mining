#
# Image for ProM Server
#

FROM zhicwu/prom:6.8

ENV GROOVY_HOME=$PROM_HOME/groovy GROOVY_VERSION=2.5.0 START_SERVER_SCRIPT=startServer.sh \
    APACHE_BASE_URL=https://dist.apache.org/repos/dist/release CAFFEINE_VERSION=2.6.2

RUN wget --progress=dot:giga $APACHE_BASE_URL/groovy/$GROOVY_VERSION/distribution/apache-groovy-binary-$GROOVY_VERSION.zip \
        $MAVEN_BASE_URL/com/github/ben-manes/caffeine/caffeine/$CAFFEINE_VERSION/caffeine-$CAFFEINE_VERSION.jar \
    && unzip *.zip \
    && mv groovy* $GROOVY_HOME \
    && mv caffeine*.jar $GROOVY_HOME/lib/ \
    && rm -f *.zip $GROOVY_HOME/bin/*.bat \
    && echo "JAVA_HOME=$JAVA_HOME" >> $PROM_HOME/.bashrc \
    && echo "PROM_HOME=$PROM_HOME" >> $PROM_HOME/.bashrc \
    && echo "GROOVY_HOME=$GROOVY_HOME" >> $PROM_HOME/.bashrc \
    && sed -i -e 's|^\(STARTER_MAIN_CLASS=.*\)|\1\nJAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:OnOutOfMemoryError=/usr/bin/oom_killer -javaagent:'"${PROM_HOME}"'/jmx_prometheus_javaagent-'"${JMX_EXPORTER_VERSION}"'.jar=1234:'"${PROM_HOME}"'/config.yaml $JAVA_OPTS"|' \
        $GROOVY_HOME/bin/startGroovy \
    && sed -i -e 's|\(^else$\)|elif [ "$1" == "server" ]; then\n    init_vnc\n    exec /sbin/setuser $PROM_USER $GROOVY_HOME/bin/groovy server.groovy\n\1|' \
        docker-entrypoint.sh

COPY content ./

# 1234 - monitoring(Prometheus)
# 5678 - REST services
# 5900 - VNC
EXPOSE 1234 5678 5900

CMD ["server"]
