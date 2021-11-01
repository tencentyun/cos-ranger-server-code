package org.apache.hadoop.fs.cosn.ranger.ranger;

import com.google.common.collect.Sets;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.PermissionRequest;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.ServiceType;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.ranger.plugin.audit.RangerDefaultAuditHandler;
import org.apache.ranger.plugin.policyengine.RangerAccessRequestImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResourceImpl;
import org.apache.ranger.plugin.policyengine.RangerAccessResult;
import org.apache.ranger.plugin.service.RangerBasePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;


public class RangerAuthorizer {
    private static final Logger log = LoggerFactory.getLogger(RangerAuthorizer.class);
    private boolean enableCOSRanger = false;
    private boolean enableChdfsRanger = false;
    private String cosServiceName;
    private String chdfsServiceName;
    private RangerBasePlugin cosRangerPlugin = null;
    private RangerBasePlugin chdfsRangerPlugin = null;

    public RangerAuthorizer(boolean enableCOSRanger, boolean enableChdfsRanger, String cosServiceName,
            String chdfsServiceName) {
        this.enableCOSRanger = enableCOSRanger;
        this.enableChdfsRanger = enableChdfsRanger;
        this.cosServiceName = cosServiceName;
        this.chdfsServiceName = chdfsServiceName;
    }

    public void init() {
        if (enableCOSRanger) {
            this.cosRangerPlugin = new RangerBasePlugin("cos", this.cosServiceName);
            this.cosRangerPlugin.init();
            this.cosRangerPlugin.setResultProcessor(new RangerDefaultAuditHandler());
        }

        if (enableChdfsRanger) {
            this.chdfsRangerPlugin = new RangerBasePlugin("chdfs", this.chdfsServiceName);
            this.chdfsRangerPlugin.init();
            this.chdfsRangerPlugin.setResultProcessor(new RangerDefaultAuditHandler());
        }
    }

    public void stop() {
        if (this.cosRangerPlugin != null) {
            this.cosRangerPlugin.cleanup();
            this.cosRangerPlugin = null;
        }

        if (this.chdfsRangerPlugin != null) {
            this.chdfsRangerPlugin.cleanup();
            this.chdfsRangerPlugin = null;
        }
    }

    public boolean checkPermission(PermissionRequest permissionReq, UserGroupInformation ugi, int retryIndex,
            int maxRetry) {
        if (permissionReq.getServiceType() == ServiceType.COS) {
            if (!enableCOSRanger) {
                log.error("cos ranger service is disabled");
                return false;
            }
            if (this.cosRangerPlugin == null) {
                log.error("cos ranger plugin is not inited");
                return false;
            }

        }
        if (permissionReq.getServiceType() == ServiceType.CHDFS) {
            if (!enableChdfsRanger) {
                log.error("chdfs ranger service is disabled");
                return false;
            }

            if (this.chdfsRangerPlugin == null) {
                log.error("chdfs ranger plugin is not inited");
                return false;
            }
        }

        RangerAccessRequestImpl rangerAccessReq = new RangerAccessRequestImpl();
        rangerAccessReq.setUser(ugi.getShortUserName());
        rangerAccessReq.setUserGroups(Sets.newHashSet(ugi.getGroupNames()));

        rangerAccessReq.setAccessTime(new Date());
        rangerAccessReq.setAccessType(permissionReq.getAccessType().toString().toLowerCase());


        RangerAccessResourceImpl rangerResource = new RangerAccessResourceImpl();
        if (permissionReq.getServiceType() == ServiceType.COS) {
            rangerResource.setValue("bucket", permissionReq.getBucketName());
            rangerResource.setValue("path", permissionReq.getObjectKey());
        }

        if (permissionReq.getServiceType() == ServiceType.CHDFS) {
            rangerResource.setValue("mountpoint", permissionReq.getFsMountPoint());
            rangerResource.setValue("path", permissionReq.getChdfsPath());
        }

        rangerAccessReq.setResource(rangerResource);

        RangerAccessResult result = null;
        if (permissionReq.getServiceType() == ServiceType.COS) {
            result = this.cosRangerPlugin.isAccessAllowed(rangerAccessReq);
        } else if (permissionReq.getServiceType() == ServiceType.CHDFS) {
            result = this.chdfsRangerPlugin.isAccessAllowed(rangerAccessReq);
        } else {
            log.error("unknown permission request service type {}", permissionReq.getServiceType());
        }
        if (result == null) {
            log.warn("check permission result is null");
            return false;
        }

        if (result.getIsAllowed()) {
            return true;
        } else {
            if (retryIndex < maxRetry) {
                log.warn("permission denied. retry_info: [{} / {}], request: {}, response: {}", retryIndex, maxRetry,
                        rangerAccessReq, result);
            } else {
                log.error("permission denied. retry_info: [{} / {}], request: {}, response: {}", retryIndex, maxRetry,
                        rangerAccessReq, result);
            }
            return false;
        }
    }
}
