package org.apache.hadoop.fs.cosn.ranger.status;

import com.qcloud.cos.utils.DateUtils;
import org.json.JSONObject;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class StatusExporter {
    public static final StatusExporter INSTANCE = new StatusExporter();

    private long serviceStartTimeMillSec;
    private volatile boolean currentNodeIsLeader;
    private volatile long becomeLeaderTimeMillSec;
    private volatile String leaderAddress;
    private AtomicLong checkPermissionAllowCnt;
    private AtomicLong checkPermissionDenyCnt;

    private StatusExporter() {
        currentNodeIsLeader = false;
        becomeLeaderTimeMillSec = 0;
        leaderAddress = "";
        serviceStartTimeMillSec = System.currentTimeMillis();
        checkPermissionAllowCnt = new AtomicLong(0L);
        checkPermissionDenyCnt = new AtomicLong(0L);
    }

    public void increasePermissionAllowCnt() {
        this.checkPermissionAllowCnt.incrementAndGet();
    }

    public void increasePermissionDenyCnt() {
        this.checkPermissionDenyCnt.incrementAndGet();
    }

    public void setLeaderAddress(boolean currentNodeIsLeader, String leaderAddr) {
        if (leaderAddr == null) {
            leaderAddr = "";
        }
        this.currentNodeIsLeader = currentNodeIsLeader;
        if (!this.leaderAddress.equals(leaderAddr)) {
            this.leaderAddress = leaderAddr;
            this.becomeLeaderTimeMillSec = System.currentTimeMillis();
        }
    }

    public String export() {
        JSONObject exportJson = new JSONObject();
        exportJson.put("serviceStartTime", DateUtils.formatISO8601Date(new Date(this.serviceStartTimeMillSec)));
        exportJson.put("currentNodeIsLeader", this.currentNodeIsLeader);
        if (this.currentNodeIsLeader) {
            exportJson.put("becomeLeaderTime", DateUtils.formatISO8601Date(new Date(this.becomeLeaderTimeMillSec)));
        }
        exportJson.put("leaderAddress", this.leaderAddress);
        exportJson.put("checkPermissionAllowCnt", checkPermissionAllowCnt.get());
        exportJson.put("checkPermissionDenyCnt", checkPermissionDenyCnt.get());
        return exportJson.toString();
    }
}
