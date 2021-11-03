package org.apache.hadoop.fs.cosn.ranger.master;

import java.io.IOException;

public interface MasterControl {
    boolean isLeader();

    void start() throws IOException;

    void join();

    void stop();
}
