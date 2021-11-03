package org.apache.hadoop.fs.cosn.ranger.constants;

public class ObjectStorageConstants {
    // server address
    public static final String QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY = "qcloud.object.storage.rpc.address";

    public static final String QCLOUD_OBJECT_STORAGE_RANGER_SERVICE_NAME_KEY =
            "qcloud.object.storage.ranger.service.name";
    public static final String DEFAULT_QCLOUD_OBJECT_STORAGE_RANGER_SERVICE_NAME = "0.0.0.0:9999";

    public static final int QCLOUD_OBJECT_STORAGE_RPC_PORT_DEFAULT = 9999;

    public static final String QCLOUD_OBJECT_STORAGE_STATUS_PORT = "qcloud.object.storage.status.port";
    public static final int QCLOUD_OBJECT_STORAGE_STATUS_PORT_DEFAULT = 9998;

    public static final String QCLOUD_OBJECT_STORAGE_STATUS_HTTP_PATH = "/status";


    // rpc handler count
    public static final String QCLOUD_OBJECT_STORAGE_RPC_HANDLER_COUNT_KEY = "qcloud.object.storage.rpc.handler.count";
    public static final int QCLOUD_OBJECT_STORAGE_RPC_HANDLER_COUNT_DEFAULT = 32;

    public static final String QCLOUD_OBJECT_STORAGE_KERBEROS_ENABLE = "qcloud.object.storage.kerberos.enable";

    // principal key
    public static final String QCLOUD_OBJECT_STORAGE_KERBEROS_PRINCIPAL_KEY =
            "qcloud.object.storage.kerberos.principal";
    public static final String QCLOUD_OBJECT_STORAGE_KERBEROS_KEYTAB_KEY = "qcloud.object.storage.kerberos.keytab";


    public static final String QCLOUD_OBJECT_STORAGE_STS_FROM_EMR_FLAG = "qcloud.object.storage.sts.from.emr.enable";

    // secret Id
    public static final String QCLOUD_OBJECT_STORAGE_COS_SECRET_ID = "qcloud.object.storage.cos.secret.id";
    // secret key
    public static final String QCLOUD_OBJECT_STORAGE_COS_SECRET_KEY = "qcloud.object.storage.cos.secret.key";
    // temp secret info duration
    public static final String QCLOUD_OBJECT_STORAGE_COS_TEMP_SECRET_DURATION =
            "qcloud.object.storage.cos.tmp.secret.duration";
    public static final int DEFAULT_QCLOUD_OBJECT_STORAGE_COS_TEMP_SECRET_DURATION = 300;

    // proto name
    public static final String QCLOUD_OBJECT_STORAGE_PROTOCOL_NAME =
            "org.apache.hadoop.fs.cosn.ranger.protocol.ClientQcloudObjectStorageProtocol";

    // security policy acl
    public static final String QCLOUD_OBJECT_STORAGE_SECURITY_POLICY_ACL = "security.object.storage.protocol.acl";

    // Delegation token related keys
    public static final String QCLOUD_OBJECT_STORAGE_DELEGATION_KEY_UPDATE_INTERVAL_KEY =
            "qcloud.object.storage.delegation.key.update-interval";
    public static final long QCLOUD_OBJECT_STORAGE_DELEGATION_KEY_UPDATE_INTERVAL_DEFAULT = 24 * 60 * 60 * 1000;
    // 1 day
    public static final String QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_RENEW_INTERVAL_KEY =
            "qcloud.object.storage.delegation.token.renew-interval";
    public static final long QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_RENEW_INTERVAL_DEFAULT = 24 * 60 * 60 * 1000;
    // 1 day
    public static final String QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_MAX_LIFETIME_KEY =
            "qcloud.object.storage.delegation.token.max-lifetime";
    public static final long QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_MAX_LIFETIME_DEFAULT = 7 * 24 * 60 * 60 * 1000;
    // 7 days
    public static final String QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_REMOVE_SCAN_INTERVAL_KEY =
            "qcloud.object.storage.delegation.token.remove-scan-interval";
    public static final long QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_REMOVE_SCAN_INTERVAL_DEFAULT = 60 * 60 * 1000;
    // 1 hours;

    // status persist path
    public static final String QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_PERSIST_DIR_PATH_KEY =
            "qcloud.object.storage.delegation.token.persist.dir.path";

    // config file path
    public static final String QCLOUD_OBJECT_STORAGE_CONFIG_DIR_KEY = "qcloud_object_storage_ranger_service_config_dir";

    // zk config
    public static final String QCLOUD_OBJECT_STORAGE_ZK_ADDRESS = "qcloud.object.storage.zk.address";
    public static final String QCLOUD_OBJECT_STORAGE_ZK_LATCH_PATH = "qcloud.object.storage.zk.latch.path";
    public static final String DEFAULT_QCLOUD_OBJECT_STORAGE_ZK_LATCH_PATH = "/ranger_qcloud_object_storage";

    public static final String QCLOUD_OBJECT_STORAGE_ZK_LEADER_IP_PATH = "qcloud.object.storage.zk.leader.ip.path";
    public static final String DEFAULT_QCLOUD_OBJECT_STORAGE_ZK_LEADER_IP_PATH =
            "/ranger_qcloud_object_storage_leader_ip";

    // sts domain
    public static final String QCLOUD_OBJECT_STORAGE_STS_DOMAIN = "qcloud.object.storage.sts.domain";
    public static final String DEFAULT_QCLOUD_OBJECT_STORAGE_STS_DOMAIN = "sts.internal.tencentcloudapi.com";

    // service support
    public static final String QCLOUD_OBJECT_STORAGE_ENABLE_COS_RANGER = "qcloud.object.storage.enable.cos.ranger";
    public static final String QCLOUD_OBJECT_STORAGE_ENABLE_CHDFS_RANGER = "qcloud.object.storage.enable.chdfs.ranger";
    public static final String QCLOUD_OBJECT_STORAGE_RANGER_COS_SERVICE_NAME = "ranger.plugin.cos.service.name";
    public static final String QCLOUD_OBJECT_STORAGE_RANGER_CHDFS_SERVICE_NAME = "ranger.plugin.chdfs.service.name";

    // ranger policy url
    public static final String QCLOUD_OBJECT_STORAGE_RANGER_PLUGIN_POLICY_URL = "ranger.plugin.chdfs.policy.rest.url";

    // check permission cnt
    public static final String QCLOUD_OBJECT_STORAGE_PERMISSION_CHECK_MAX_RETRY_KEY =
            "qcloud.object.storage.permission.check.max.retry";

    public static final int DEFAULT_QCLOUD_OBJECT_STORAGE_PERMISSION_CHECK_MAX_RETRY = 5;
}
