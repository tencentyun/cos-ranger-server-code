package org.apache.hadoop.fs.cosn.ranger.status;

import com.sun.net.httpserver.HttpServer;
import org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

public class StatusHttpServer {
    private static final Logger log = LoggerFactory.getLogger(StatusHttpServer.class);
    private int httpPort;

    public StatusHttpServer(int httpPort) {
        this.httpPort = httpPort;
    }

    public void start() throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
        httpServer.createContext(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_STATUS_HTTP_PATH,
                new StatusHttpHandler());

        Thread httpServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                httpServer.start();
            }
        });
        httpServerThread.setDaemon(true);
        httpServerThread.start();
    }
}
