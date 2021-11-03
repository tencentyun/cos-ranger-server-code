package org.apache.hadoop.fs.cosn.ranger.master;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants;
import org.apache.hadoop.fs.cosn.ranger.server.ClientQcloudObjectStorageProtocolRpcServer;
import org.apache.hadoop.fs.cosn.ranger.status.StatusExporter;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class ZKMasterControl implements MasterControl {
    private static final Logger log = LoggerFactory.getLogger(ZKMasterControl.class);
    private Configuration conf;
    private ClientQcloudObjectStorageProtocolRpcServer rpcServer;
    private CuratorFramework client;
    private LeaderLatch leaderLatch;
    private String zkLeaderIpPath;
    private LeaderLatchListener leaderLatchListener;

    public ZKMasterControl(Configuration conf, ClientQcloudObjectStorageProtocolRpcServer rpcServer) {
        this.conf = conf;
        this.rpcServer = rpcServer;

    }

    @Override
    public boolean isLeader() {
        if (this.leaderLatch == null) {
            return false;
        }
        return this.leaderLatch.hasLeadership();
    }

    @Override
    public void start() throws IOException {
        String zkAddress = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_ZK_ADDRESS);
        if (zkAddress == null || zkAddress.isEmpty()) {
            throw new IOException("invalid zk address " + zkAddress);
        }

        String zkLeaderLatchPath = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_ZK_LATCH_PATH,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_ZK_LATCH_PATH);

        if (zkLeaderLatchPath == null || zkLeaderLatchPath.isEmpty()) {
            throw new IOException("invalid zk leader path " + zkLeaderLatchPath);
        }

        this.zkLeaderIpPath = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_ZK_LEADER_IP_PATH,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_ZK_LEADER_IP_PATH);
        if (zkLeaderIpPath == null || zkLeaderIpPath.isEmpty()) {
            throw new IOException("invalid zk leader ip path " + zkLeaderIpPath);
        }


        this.client = CuratorFrameworkFactory.newClient(zkAddress, 10000, 10000, new ExponentialBackoffRetry(1000, 3));

        leaderLatch = new LeaderLatch(client, zkLeaderLatchPath);

        client.start();
        this.leaderLatchListener = new LeaderLatchListener() {
            @Override
            public void isLeader() {
                try {
                    String leaderIp = getLeaderIp();
                    log.info("current node begin to be leader, node_ip: {}", leaderIp);
                    rpcServer.start();
                    saveLeaderIpToZk(leaderIp);
                    setLeaderInfoStatus(true);
                } catch (IOException e) {
                    log.error("start rpc server failed", e);
                    System.exit(-1);
                }
            }

            @Override
            public void notLeader() {
                try {
                    String leaderIp = getLeaderIp();
                    log.info("current node stop to be leader now!, node_ip: {}", leaderIp);
                    rpcServer.stop();
                    setLeaderInfoStatus(true);
                } catch (IOException e) {
                    log.error("stop rpc server failed", e);
                    System.exit(-1);
                }
            }
        };

        leaderLatch.addListener(this.leaderLatchListener);

        try {
            leaderLatch.start();
        } catch (Exception e) {
            throw new IOException("leader latch start failed", e);
        }
    }

    private void saveLeaderIpToZk(String leaderIp) throws IOException {
        try {
            if (client.checkExists().forPath(zkLeaderIpPath) != null) {
                client.setData().forPath(zkLeaderIpPath, leaderIp.getBytes());
            } else {
                client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(zkLeaderIpPath,
                        leaderIp.getBytes());
            }
        } catch (Exception e) {
            throw new IOException("save leader ip to zk failed", e);
        }
    }

    private String getLeaderIp() throws IOException {
        InetSocketAddress rpcServerAddress = rpcServer.getRpcServerAddress();
        String leaderIp = String.format("%s:%d", rpcServerAddress.getAddress().getHostAddress(),
                rpcServerAddress.getPort());
        return leaderIp;
    }

    private void setLeaderInfoStatus(boolean printLeaderInfo) {
        String leaderIp = null;
        try {
            leaderIp = new String(client.getData().forPath(zkLeaderIpPath));
        } catch (Exception e) {
            log.error("get leader ip from zk failed", e);
            return;
        }
        if (leaderLatch.hasLeadership()) {
            if (printLeaderInfo) {
                log.info("current node is leader now, leader_ip is {}", leaderIp);
            }
            StatusExporter.INSTANCE.setLeaderAddress(true, leaderIp);
        } else {
            if (printLeaderInfo) {
                log.info("current node is not leader now, leader_ip is {}", leaderIp);
            }
            StatusExporter.INSTANCE.setLeaderAddress(false, leaderIp);
        }
    }

    @Override
    public void join() {
        setLeaderInfoStatus(true);
        while (true) {
            try {
                if (leaderLatch.hasLeadership()) {
                    Thread.sleep(1000);
                } else {
                    leaderLatch.await(1000, TimeUnit.MILLISECONDS);
                }
                setLeaderInfoStatus(false);
            } catch (InterruptedException e) {
                log.error("leader latch await is interrupted", e);
            }
        }
    }

    @Override
    public void stop() {
        log.info("stop zk master");
        this.rpcServer.stop();
        if (this.leaderLatchListener != null) {
            this.leaderLatch.removeListener(leaderLatchListener);
        }

        try {
            this.leaderLatch.close();
            this.client.close();
        } catch (IOException e) {
            log.error("close leader latch occur a exception", e);
        }
    }
}
