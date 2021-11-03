package org.apache.hadoop.fs.cosn.ranger.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusHttpHandler implements HttpHandler {
    private ExecutorService threadPool = Executors.newFixedThreadPool(4);

    @Override
    public void handle(final HttpExchange httpExchange) throws IOException {
        this.threadPool.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String statusStr = StatusExporter.INSTANCE.export();
                httpExchange.sendResponseHeaders(200, statusStr.length());
                OutputStream out = httpExchange.getResponseBody();
                out.write(statusStr.getBytes());
                out.close();
                return null;
            }
        });
    }
}
