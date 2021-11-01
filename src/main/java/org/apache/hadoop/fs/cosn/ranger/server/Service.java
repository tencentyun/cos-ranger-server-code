package org.apache.hadoop.fs.cosn.ranger.server;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants;
import org.apache.hadoop.fs.cosn.ranger.master.MasterControl;
import org.apache.hadoop.fs.cosn.ranger.master.ZKMasterControl;
import org.apache.hadoop.fs.cosn.ranger.status.StatusHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class Service {
    private static final Logger log = LoggerFactory.getLogger(Service.class);

    public static void main(String[] args) {
        Configuration conf = buildConfiguration();
        ClientQcloudObjectStorageProtocolRpcServer rpcServer = new ClientQcloudObjectStorageProtocolRpcServer(conf);

        int statusHttpServerPort = conf.getInt(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_STATUS_PORT,
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_STATUS_PORT_DEFAULT);

        StatusHttpServer shs = new StatusHttpServer(statusHttpServerPort);
        try {
            shs.start();
        } catch (IOException e) {
            log.error("start http server failed", e);
            return;
        }

        try {
            rpcServer.init();
        } catch (IOException e) {
            log.error("init rpc server failed", e);
            return;
        }

        MasterControl masterControl = new ZKMasterControl(conf, rpcServer);
        rpcServer.setMasterControl(masterControl);
        try {
            masterControl.start();
        } catch (IOException e) {
            log.error("start service failed", e);
            return;
        }

        ShutDownHookThread shutDownHookThread = new ShutDownHookThread();
        shutDownHookThread.setMasterControl(masterControl);
        Runtime.getRuntime().addShutdownHook(shutDownHookThread);

        masterControl.join();

        log.info("cos ranger service exit!");
    }

    private static Configuration buildConfiguration() {
        Configuration conf = new Configuration();
        String confDirPath = System.getenv(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_CONFIG_DIR_KEY);
        if (confDirPath == null) {
            throw new RuntimeException(
                    String.format("conf %s is missing!", ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_CONFIG_DIR_KEY));
        }
        File confDir = new File(confDirPath);
        if (!confDir.isDirectory()) {
            throw new RuntimeException(String.format("conf dir %s not exist!", confDirPath));
        }
        String[] confNameArray = {
                "cos-ranger.xml",
                "ranger-cos-audit.xml",
                "ranger-cos-security.xml",
                "ranger-chdfs-audit.xml",
                "ranger-chdfs-security.xml"};
        for (String confName : confNameArray) {
            File confFile = new File(confDir, confName);
            if (confFile.exists() && confFile.canRead()) {
                conf.addResource(new Path(confFile.getAbsolutePath()));
            }
        }
        conf.addResource("hadoop-policy.xml");
        conf.addResource("hdfs-site.xml");
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");

        conf.reloadConfiguration();

        for (String propName : System.getProperties().stringPropertyNames()) {
            conf.set(propName, System.getProperty(propName));
        }
        return conf;
    }

    private static class ShutDownHookThread extends Thread {
        private MasterControl masterControl;

        public void setMasterControl(MasterControl masterControl) {
            this.masterControl = masterControl;
        }

        @Override
        public void run() {
            if (this.masterControl != null) {
                this.masterControl.stop();
            }
        }
    }
}
