# docker-process-mining
Docker image for process mining.

This is basically [ProM](http://www.promtools.org) 6.7 with [OpenJDK](http://openjdk.java.net/) JRE 1.8 running on [Ubuntu](https://www.ubuntu.com/) 16.04.

All credit goes to who contributed to ProM, an amazing tool for process mining.

## Quick Start

* Start ProM
    ```bash
    $ docker run --rm -it -p5909:5900 zhicwu/prom:6.7
    ```
    It's a bit slow to start for the first time. Once it's ready, you should be able to see the screen by connecting to `localhost:5909` via a VNC viewer(default password is `secret`).

* Run Script
    ```bash
    $ cat test.js
    print('Hello ProM!');
    $ docker run --rm -it -v`pwd`/test.js:/test.js zhicwu/prom:6.7 /test.js
    Starting xvfb in background...
    Setup display...
    nohup: redirecting stderr to stdout
    nashorn full version 1.8.0_171-8u171-b11-0ubuntu0.16.04.1-b11
    Hello ProM!
    ```

* Upgrade Plugins
    ```bash
    $ docker run --rm -it zhicwu/prom:6.7 bash
    # du -sh .ProM67
    774M	.ProM67
    # ls -alF .ProM67
    total 24
    drwxr-xr-x   3 prom prom  4096 Jul  4 04:31 ./
    drwxr-xr-x   1 prom prom  4096 Jul  4 04:31 ../
    drwxr-xr-x 209 prom prom 12288 Jul  4 04:54 packages/
    # ./docker-entrypoint.sh scripts/install.js
    ```

## TODOs

- [x] ~~Dockerize ProM~~
- [ ] Use Groovy instead of JavaScript or BeanShell for scripting
- [ ] Embedded lightweight web server and REST APIs