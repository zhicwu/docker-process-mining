# docker-process-mining
Docker image for process mining.

This is basically [ProM](http://www.promtools.org) 6 with [OpenJDK](http://openjdk.java.net/) JRE 1.8 running on [Ubuntu](https://www.ubuntu.com/) 16.04.

All credit goes to who contributed to ProM, an amazing tool for process mining.

## Quick Start

* Start ProM
    ```bash
    $ docker run --rm -it -p5909:5900 zhicwu/prom:6
    ```
    It's a bit slow to start for the first time. Once it's ready, you should be able to see the screen by connecting to `localhost:5909` via a VNC viewer(default password is `secret`).

* Run JavaScript(ES6 by default)
    ```bash
    $ cat test.js
    const message = 'Hello ProM!';
    print(message);
    $ docker run --rm -it -v`pwd`/test.js:/test.js zhicwu/prom:6 /test.js
    Starting xvfb in background...
    Setup display...
    nohup: redirecting stderr to stdout
    nashorn full version 1.8.0_171-8u171-b11-0ubuntu0.16.04.1-b11
    Hello ProM!
    ```

* Upgrade Plugins
    ```bash
    $ docker run --rm -it zhicwu/prom:6 bash
    # du -sh .ProM68
    1.2G	.ProM68
    # ls -alF .ProM68
    total 24
    drwxr-xr-x   3 prom prom  4096 Jul  8 17:23 ./
    drwxr-xr-x   1 prom prom  4096 Jul  9 06:47 ../
    drwxr-xr-x 237 prom prom 12288 Jul  8 17:29 packages/
    # ./docker-entrypoint.sh install.js
    ```

* Run in Server Mode([sample script](/prom6-server/content/scripts/test.groovy))
    ```bash
    $ docker run --rm -it -p1234:1234 -p5678:5678 -p5900:5900 zhicwu/prom:6-server
    .
    .
    .
    Starting service http://localhost:5678/script...
    $ time curl http://localhost:5678/script?file=test
    {"id":"dd27f950-4522-41dc-aad3-d43b0e8fda74","script":"test.groovy","result":2333}
    real	0m3.875s
    user	0m0.021s
    sys	0m0.007s
    $ curl -X POST -d'println 2333' http://localhost:5678/script?file=mine
    ...
    $ curl http://localhost:5678/script?file=mine
    ```

## TODOs

- [x] ~~Dockerize ProM~~
- [x] ~~Use Groovy instead of JavaScript or BeanShell for scripting~~
- [x] ~~Embedded lightweight web server and REST APIs~~
- [ ] Streaming
- [ ] Inductive mining