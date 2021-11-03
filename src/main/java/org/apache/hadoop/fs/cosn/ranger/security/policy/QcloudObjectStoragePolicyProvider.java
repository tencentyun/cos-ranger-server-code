package org.apache.hadoop.fs.cosn.ranger.security.policy;

import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQcloudObjectStorageProtocol;
import org.apache.hadoop.security.authorize.Service;

import static org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_SECURITY_POLICY_ACL;

public class QcloudObjectStoragePolicyProvider extends org.apache.hadoop.security.authorize.PolicyProvider {
    private static final Service[] qcloudObjectStoragePolicyProvider = new Service[]{new Service(
            QCLOUD_OBJECT_STORAGE_SECURITY_POLICY_ACL, ClientQcloudObjectStorageProtocol.class)};


    @Override
    public Service[] getServices() {
        return qcloudObjectStoragePolicyProvider;
    }
}
