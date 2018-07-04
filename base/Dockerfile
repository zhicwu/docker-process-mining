#
# Base image for ProM
#

FROM phusion/baseimage:0.10.1

LABEL maintainer="zhicwu@gmail.com"

ENV PROM_HOME=/prom PROM_USER=prom \
	LANG="en_US.UTF-8" LANGUAGE="en_US.UTF-8" LC_ALL="en_US.UTF-8" \
	TERM=xterm JAVA_VERSION=8 JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

RUN locale-gen $LANG \
	&& echo 'APT::Install-Recommends 0;' >> /etc/apt/apt.conf.d/01norecommends \
	&& echo 'APT::Install-Suggests 0;' >> /etc/apt/apt.conf.d/01norecommends \
	&& apt-get update \
	&& DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-unauthenticated tzdata net-tools \
		netcat wget curl iputils-ping iotop iftop tcpdump lsof htop iptraf git bzip2 unzip xz-utils \
		openjdk-8-jre ca-certificates-java libltdl7 xvfb libxrender-dev libxtst-dev x11vnc \
	&& printf '12\n10\n' | dpkg-reconfigure -f noninteractive tzdata \
	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/* \
	&& echo '#!/bin/bash' > /usr/bin/oom_killer \
			&& echo 'set -e' >> /usr/bin/oom_killer \
			&& echo 'echo "`date +"%Y-%m-%d %H:%M:%S.%N"` OOM Killer activated! PID=$PID, PPID=$PPID"' >> /usr/bin/oom_killer \
			&& echo 'ps -auxef' >> /usr/bin/oom_killer \
			&& echo 'for pid in $(jps | grep -v Jps | awk '"'"'{print $1}'"'"'); do echo "-> Killing $pid..." && kill -9 $pid || true; done' >> /usr/bin/oom_killer \
			&& chmod +x /usr/bin/oom_killer \
	&& sed -i -e 's|.*\(networkaddress.cache.ttl\)=.*|\1=30|' ${JAVA_HOME}/jre/lib/security/java.security \
	&& useradd -md $PROM_HOME -s /bin/bash $PROM_USER

WORKDIR $PROM_HOME
