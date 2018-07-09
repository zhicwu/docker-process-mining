#!/bin/bash
set -e

: ${JAVA_OPTS:="-XX:+TieredCompilation -XX:+UseStringDeduplication -XX:-AlwaysPreTouch -XX:+ScavengeBeforeFullGC XX:+PreserveFramePointer -Djava.security.egd=file:/dev/./urandom -XX:ErrorFile=./logs/jvm_error.log -verbose:gc -Xloggc:./logs/gc.log -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintHeapAtGC -XX:+PrintAdaptiveSizePolicy -XX:+PrintStringDeduplicationStatistics -XX:+PrintTenuringDistribution -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=2 -XX:GCLogFileSize=64M -Dsun.rmi.dgc.client.gcInterval=3600000 -Dsun.rmi.dgc.server.gcInterval=3600000"}

: ${DISPLAY_ID:=":1"}
: ${DISPLAY_RESOLUTION:="1024x768x24"}

: ${VNC_PASSWORD:="secret"}

: ${CONNECT_TIMEOUT:=""}
: ${READ_TIMEOUT:=""}

update_config() {
    if [ "$CONNECT_TIMEOUT" != "" ]; then
        sed -i -e "s|\(^CONNECT_TIMEOUT[[:space:]]*=\).*|\1 $CONNECT_TIMEOUT|" $PROM_HOME/ProM.ini
    fi

    if [ "$READ_TIMEOUT" != "" ]; then
        sed -i -e "s|\(^READ_TIMEOUT[[:space:]]*=\).*|\1 $READ_TIMEOUT|" $PROM_HOME/ProM.ini
    fi
}

init_display() {
    if [ "$DISPLAY" == "" ] || [ "$(pidof Xvfb)" == "" ]; then
        echo "Starting xvfb in background..."
        nohup Xvfb $DISPLAY_ID -screen 0 $DISPLAY_RESOLUTION -ac +extension GLX +render >/dev/null &

        echo "Setup display..."
        export DISPLAY=$DISPLAY_ID
    fi
}

init_vnc() {
    init_display

    if [ "$(pidof x11vnc)" == "" ]; then
        x11vnc -storepasswd "$VNC_PASSWORD" /etc/vncsecret

        nohup x11vnc -rfbauth /etc/vncsecret -display $DISPLAY_ID -forever -shared >/dev/null &
    fi
}

run_prom() {
    init_vnc

    exec /sbin/setuser $PROM_USER ./$PROM_SCRIPT
}

run_script() {
    init_display

    exec /sbin/setuser $PROM_USER ./$PROM_CLI_SCRIPT "$@"
}

update_config

if [ "$#" -eq 0 ]; then
    run_prom
elif [[ "$1" == *".js" ]]; then
    run_script "$@"
else
    exec "$@"
fi