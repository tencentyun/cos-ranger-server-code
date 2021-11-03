package org.apache.hadoop.fs.cosn.ranger.server;

import com.google.protobuf.BlockingService;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentialsProvider;
import com.qcloud.cos.auth.InstanceCredentialsFetcher;
import com.qcloud.cos.auth.InstanceCredentialsProvider;
import com.qcloud.cos.auth.InstanceMetadataCredentialsEndpointProvider;
import com.tencent.cloud.CosStsClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants;
import org.apache.hadoop.fs.cosn.ranger.master.MasterControl;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQCloudObjectStorageProtocolProtos;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQcloudObjectStorageProtocol;
import org.apache.hadoop.fs.cosn.ranger.protocolpb.ClientQcloudObjectStorageProtocolPB;
import org.apache.hadoop.fs.cosn.ranger.protocolpb.server.ClientQcloudObjectStorageProtocolServerSideTranslatorPB;
import org.apache.hadoop.fs.cosn.ranger.ranger.RangerAuthorizer;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.PermissionRequest;
import org.apache.hadoop.fs.cosn.ranger.security.policy.QcloudObjectStoragePolicyProvider;
import org.apache.hadoop.fs.cosn.ranger.security.sts.GetSTSRequest;
import org.apache.hadoop.fs.cosn.ranger.security.sts.GetSTSResponse;
import org.apache.hadoop.fs.cosn.ranger.security.token.DelegationTokenIdentifier;
import org.apache.hadoop.fs.cosn.ranger.security.token.DelegationTokenSecretManager;
import org.apache.hadoop.fs.cosn.ranger.status.StatusExporter;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.ipc.ProtobufRpcEngine;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.ipc.Server;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.security.SecurityUtil;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_PLUGIN_POLICY_URL;
import static org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY;

public class ClientQcloudObjectStorageProtocolRpcServer implements ClientQcloudObjectStorageProtocol {
    private static final Logger log = LoggerFactory.getLogger(ClientQcloudObjectStorageProtocolRpcServer.class);
    private static final String dumpTokenFileName = "token.checkpoint";
    private Configuration conf;
    private DelegationTokenSecretManager dtSecretManager;
    private RangerAuthorizer rangerAuthorizer;
    private RPC.Server serviceRpcServer;
    private String secretId;
    private String secretKey;
    private String serviceName;
    private String persistTokenPath;
    private FileSystem persistTokenFS;
    private MasterControl masterControl;

    private boolean getSTSFromEMR;

    private COSCredentialsProvider emrCredentialsProvider;

    private int permissionMaxRetry;

    public ClientQcloudObjectStorageProtocolRpcServer(Configuration conf) {
        this.conf = conf;
    }

    public void setMasterControl(MasterControl masterControl) {
        this.masterControl = masterControl;
    }


    public void init() throws IOException {
        initDelegationTokenSecretManager();
        initRangerAuthorizer();
        initServiceName();

        initPersistTokenFS();
        startSecretManager();
        startRangerAuthorizer();

        UserGroupInformation.setConfiguration(this.conf);
        loginAsQcloudObjectStorageServerUser();

        this.getSTSFromEMR = this.conf.getBoolean(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_STS_FROM_EMR_FLAG,
                false);

        this.secretId = conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_COS_SECRET_ID);
        this.secretKey = conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_COS_SECRET_KEY);
        this.permissionMaxRetry = conf.getInt(
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_PERMISSION_CHECK_MAX_RETRY_KEY,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_PERMISSION_CHECK_MAX_RETRY);

        if (getSTSFromEMR) {
            initEmrSTSClient();
        } else {
            if (this.secretId == null || this.secretKey == null) {
                throw new IOException("miss secretId or secretKey");
            }
        }
    }

    private void initDelegationTokenSecretManager() {
        this.dtSecretManager = new DelegationTokenSecretManager(this.conf
                .getLong(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_KEY_UPDATE_INTERVAL_KEY,
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_KEY_UPDATE_INTERVAL_DEFAULT), this.conf
                .getLong(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_MAX_LIFETIME_KEY,
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_MAX_LIFETIME_DEFAULT), this.conf
                .getLong(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_RENEW_INTERVAL_KEY,
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_RENEW_INTERVAL_DEFAULT), this.conf
                .getLong(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_REMOVE_SCAN_INTERVAL_KEY,
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_REMOVE_SCAN_INTERVAL_DEFAULT));
    }

    private void initRangerAuthorizer() throws IOException {
        boolean enableCosRanger = this.conf.getBoolean(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_ENABLE_COS_RANGER,
                false);
        boolean enableChdfsRanger = this.conf.getBoolean(
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_ENABLE_CHDFS_RANGER, false);
        String cosServiceName = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_COS_SERVICE_NAME);
        String chdfsServiceName = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_CHDFS_SERVICE_NAME);

        if (enableCosRanger) {
            if (cosServiceName == null || cosServiceName.trim().isEmpty()) {
                throw new IOException(String.format("config %s is missing",
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_COS_SERVICE_NAME));
            }
        }

        if (enableChdfsRanger) {
            if (chdfsServiceName == null || chdfsServiceName.trim().isEmpty()) {
                throw new IOException(String.format("config %s is missing",
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_CHDFS_SERVICE_NAME));
            }
        }
        this.rangerAuthorizer = new RangerAuthorizer(enableCosRanger, enableChdfsRanger, cosServiceName,
                chdfsServiceName);
    }

    private void initServiceName() throws IOException {
        String address = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RANGER_SERVICE_NAME_KEY,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_RANGER_SERVICE_NAME);

        InetSocketAddress addr = NetUtils.createSocketAddr(address,
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_PORT_DEFAULT);
        String host = addr.getAddress().getHostAddress();

        this.serviceName = host + ":" + addr.getPort();
    }


    private void initPersistTokenFS() throws IOException {
        this.persistTokenPath = this.conf.get(
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_PERSIST_DIR_PATH_KEY);
        if (this.persistTokenPath == null) {
            throw new IOException("miss config "
                    + ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_PERSIST_DIR_PATH_KEY);
        }

        try {
            this.persistTokenFS = FileSystem.get(new URI(this.persistTokenPath), this.conf);
        } catch (URISyntaxException e) {
            throw new IOException("invalid uri " + this.persistTokenPath);
        }

        Path dirPath = new Path(this.persistTokenPath);

        try {
            FileStatus dirPathStatus = this.persistTokenFS.getFileStatus(dirPath);
            if (dirPathStatus.isFile()) {
                throw new IOException(String.format("config %s : %s is a file",
                        ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN_PERSIST_DIR_PATH_KEY, dirPath));
            }
        } catch (FileNotFoundException e) {
            this.persistTokenFS.mkdirs(dirPath);
        }
    }

    private void startSecretManager() {
        synchronized (dtSecretManager) {
            if (!dtSecretManager.isRunning()) {
                try {
                    dtSecretManager.startThreads();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void startRangerAuthorizer() {
        synchronized (rangerAuthorizer) {
            rangerAuthorizer.init();
        }
    }

    private void loginAsQcloudObjectStorageServerUser() throws IOException {
        if (!this.conf.getBoolean(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_KERBEROS_ENABLE, false)) {
            return;
        }
        InetSocketAddress socAddr = getRpcServerAddress();
        SecurityUtil.login(this.conf, ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_KERBEROS_KEYTAB_KEY,
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_KERBEROS_PRINCIPAL_KEY, socAddr.getHostName());
    }

    private void initEmrSTSClient() {
        InstanceMetadataCredentialsEndpointProvider endpointProvider = new InstanceMetadataCredentialsEndpointProvider(
                InstanceMetadataCredentialsEndpointProvider.Instance.EMR);
        InstanceCredentialsFetcher instanceCredentialsFetcher = new InstanceCredentialsFetcher(endpointProvider);
        this.emrCredentialsProvider = new InstanceCredentialsProvider(instanceCredentialsFetcher);
    }

    public InetSocketAddress getRpcServerAddress() throws IOException {
        String address = this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY);
        log.info("getRpcServerAddress, config: {}, conf_get: {}, prop_get: {}, deprecated: {}",
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY, address,
                System.getProperty(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY),
                Configuration.isDeprecated(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY));
        if (address == null || address.isEmpty()) {
            throw new IOException(
                    String.format("invalid address for config %s", QCLOUD_OBJECT_STORAGE_RPC_ADDRESS_KEY));
        }

        return NetUtils.createSocketAddr(address, ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_PORT_DEFAULT);
    }

    private void startRpcServer() throws IOException {
        RPC.setProtocolEngine(conf, ClientQcloudObjectStorageProtocolPB.class, ProtobufRpcEngine.class);
        ClientQcloudObjectStorageProtocolServerSideTranslatorPB serverSideTranslatorPB =
                new ClientQcloudObjectStorageProtocolServerSideTranslatorPB(this);

        BlockingService qcloudObjectStorageService =
                ClientQCloudObjectStorageProtocolProtos.ClientQcloudObjectStorageProtocol.newReflectiveBlockingService(
                        serverSideTranslatorPB);

        InetSocketAddress socAddr = getRpcServerAddress();
        int handlerCount = conf.getInt(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_HANDLER_COUNT_KEY,
                ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_RPC_HANDLER_COUNT_DEFAULT);

        serviceRpcServer = new RPC.Builder(conf).setProtocol(ClientQcloudObjectStorageProtocolPB.class).setInstance(
                qcloudObjectStorageService).setBindAddress(socAddr.getHostName()).setPort(socAddr.getPort())
                                                .setNumHandlers(handlerCount).setVerbose(false).setSecretManager(
                        dtSecretManager).build();

        if (this.conf.getBoolean(CommonConfigurationKeys.HADOOP_SECURITY_AUTHORIZATION, false)) {
            serviceRpcServer.refreshServiceAcl(this.conf, new QcloudObjectStoragePolicyProvider());
        }
        serviceRpcServer.start();
    }

    public synchronized void start() throws IOException {
        startSecretManager();
        initPersistTokenFS();
        reloadPersistToken();
        startRpcServer();
    }

    public synchronized void stop() {
        if (serviceRpcServer != null) {
            serviceRpcServer.stop();
            serviceRpcServer = null;
        }
        try {
            saveDelegationTokenState();
        } catch (IOException e) {
            log.error("save delegation token failed!", e);
        }
        closePersistTokenFS();
        stopSecretManager();
    }

    private void stopSecretManager() {
        synchronized (dtSecretManager) {
            if (dtSecretManager.isRunning()) {
                dtSecretManager.stopThreads();
                dtSecretManager.reset();
            }
        }
    }

    private void closePersistTokenFS() {
        if (this.persistTokenFS != null) {
            try {
                this.persistTokenFS.close();
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public Token<DelegationTokenIdentifier> getDelegationToken(Text renewer) throws IOException {
        if (masterControl == null || !masterControl.isLeader()) {
            throw new IOException("current node is not leader");
        }
        if (dtSecretManager == null || !dtSecretManager.isRunning()) {
            log.warn("trying to get DT with no secret manager running");
            throw new IOException("secret manager is not running");
        }

        UserGroupInformation ugi = getRemoteUser();
        String user = ugi.getUserName();
        Text owner = new Text(user);
        Text realUser = null;
        if (ugi.getRealUser() != null) {
            realUser = new Text(ugi.getRealUser().getUserName());
        }

        DelegationTokenIdentifier dtId = new DelegationTokenIdentifier(owner, renewer, realUser);
        Token<DelegationTokenIdentifier> token = new Token<DelegationTokenIdentifier>(dtId, dtSecretManager);
        token.setService(new Text(this.serviceName));

        saveDelegationTokenState();
        long expiryTime = dtSecretManager.getTokenExpiryTime(dtId);


        log.info("getDelegationToken, [delegationTokenIdentifier: {}], [expiryTime: {}]", dtId.toString(),
                formatDate(new Date(expiryTime)));
        return token;
    }

    private static UserGroupInformation getRemoteUser() throws IOException {
        UserGroupInformation ugi = Server.getRemoteUser();
        return (ugi != null) ? ugi : UserGroupInformation.getCurrentUser();
    }

    private void reloadPersistToken() throws IOException {
        Path dirPath = new Path(this.persistTokenPath);
        Path dumpTokenPath = new Path(dirPath, dumpTokenFileName);
        FSDataInputStream in = null;
        try {
            in = this.persistTokenFS.open(dumpTokenPath);
            this.dtSecretManager.load(in);
        } catch (FileNotFoundException ignore) {
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private void saveDelegationTokenState() throws IOException {
        final long startTimeMs = System.currentTimeMillis();
        String tmpFilePath = dumpTokenFileName + ".tmp";
        Path dumpTokenPath = new Path(this.persistTokenPath, dumpTokenFileName);
        Path dumpTokenTmpPath = new Path(this.persistTokenPath, tmpFilePath);
        FSDataOutputStream out = null;
        try {
            out = this.persistTokenFS.create(dumpTokenTmpPath, true);
            this.dtSecretManager.save(out);
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }

        this.persistTokenFS.delete(dumpTokenPath, true);
        this.persistTokenFS.rename(dumpTokenTmpPath, dumpTokenPath);
        log.info("save delegationToken usedTime: {} ms", System.currentTimeMillis() - startTimeMs);
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    @Override
    public long renewDelegationToken(Token<DelegationTokenIdentifier> token) throws IOException {
        if (masterControl == null || !masterControl.isLeader()) {
            throw new IOException("current node is not leader");
        }
        String renewer = getRemoteUser().getShortUserName();
        long expiryTime = dtSecretManager.renewToken(token, renewer);
        saveDelegationTokenState();

        final DelegationTokenIdentifier dtId = DelegationTokenIdentifier.decodeDelegationToken(token);
        log.info("renewDelegationToken, [renewer: {}], [DelegationTokenIdentifier: {}], [expiryTime: {}]", renewer,
                dtId.toString(), formatDate(new Date(expiryTime)));
        return expiryTime;
    }

    @Override
    public void cancelDelegationToken(Token<DelegationTokenIdentifier> token) throws IOException {
        if (masterControl == null || !masterControl.isLeader()) {
            throw new IOException("current node is not leader");
        }
        String canceller = getRemoteUser().getUserName();
        DelegationTokenIdentifier dtId = dtSecretManager.cancelToken(token, canceller);

        log.info("cancelDelegationToken, [canceller: {}], [DelegationTokenIdentifier: {}]", canceller, dtId.toString());
    }

    private void backOff(int retryIndex) {
        if (retryIndex < 0) {
            return;
        }

        int leastRetryTime = Math.min(retryIndex * 50, 1500);
        int sleepMs = ThreadLocalRandom.current().nextInt(leastRetryTime, 2000);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
        }
    }

    @Override
    public boolean checkPermission(PermissionRequest permissionReq) throws IOException {
        if (masterControl == null || !masterControl.isLeader()) {
            throw new IOException("current node is not leader");
        }
        UserGroupInformation ugi = getRemoteUser();

        boolean allow = false;
        for (int retryIndex = 1; retryIndex <= this.permissionMaxRetry; ++retryIndex) {
            allow = this.rangerAuthorizer.checkPermission(permissionReq, ugi, retryIndex, this.permissionMaxRetry);
            if (allow) {
                break;
            }
            backOff(retryIndex);
        }

        if (allow) {
            StatusExporter.INSTANCE.increasePermissionAllowCnt();
        } else {
            StatusExporter.INSTANCE.increasePermissionDenyCnt();
        }
        return allow;
    }

    @Override
    public GetSTSResponse getSTS(GetSTSRequest request) throws IOException {
        if (masterControl == null || !masterControl.isLeader()) {
            throw new IOException("current node is not leader");
        }
        if (getSTSFromEMR) {
            return getSTSFromEmr(request);
        } else {
            return getSTSFromCam(request);
        }
    }

    @Override
    public String getRangerPolicyUrl() throws IOException {
        String policyUrl = this.conf.get(QCLOUD_OBJECT_STORAGE_RANGER_PLUGIN_POLICY_URL, "");
        final String httpPrefix = "http://";
        final String httpsPrefix = "https://";
        if (policyUrl.startsWith(httpPrefix)) {
            return policyUrl.substring(httpPrefix.length());
        }
        if (policyUrl.startsWith(httpsPrefix)) {
            return policyUrl.substring(httpsPrefix.length());
        }
        return policyUrl;
    }

    private GetSTSResponse getSTSFromEmr(GetSTSRequest request) throws IOException {
        final int maxRetry = 3;
        for (int retryIndex = 1; retryIndex <= maxRetry; ++retryIndex) {
            BasicSessionCredentials cred = (BasicSessionCredentials) this.emrCredentialsProvider.getCredentials();
            if (cred == null) {
                continue; // retry
            }
            GetSTSResponse stsResponse = new GetSTSResponse();
            stsResponse.setTempAK(cred.getCOSAccessKeyId());
            stsResponse.setTempSK(cred.getCOSSecretKey());
            stsResponse.setTempToken(cred.getSessionToken());
            return stsResponse;
        }
        throw new IOException("get sts from emr failed after retry");
    }

    private GetSTSResponse getSTSFromCam(GetSTSRequest request) throws IOException {
        TreeMap<String, Object> config = new TreeMap<>();

        config.put("bucket", request.getBucketName());
        config.put("region", request.getBucketRegion());

        config.put("secretId", secretId);
        config.put("secretKey", secretKey);
        int tmpDurationSeconds = this.conf.getInt(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_COS_TEMP_SECRET_DURATION,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_COS_TEMP_SECRET_DURATION);
        config.put("durationSeconds", tmpDurationSeconds);


        String allowPrefix = request.getAllowPrefix();
        if (allowPrefix == null || allowPrefix.isEmpty()) {
            allowPrefix = "*";
        }
        config.put("allowPrefix", allowPrefix);

        config.put("host", this.conf.get(ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_STS_DOMAIN,
                ObjectStorageConstants.DEFAULT_QCLOUD_OBJECT_STORAGE_STS_DOMAIN));

        String[] allowActions = new String[]{
                //                "name/cos:PutObject",
                ////                "name/cos:GetObject",
                ////                "name/cos:DelObject",
                ////                "name/cos:PutObjectCopy",
                ////                "name/cos:InitiateMultipartUpload",
                ////                "name/cos:ListMultipartUploads",
                ////                "name/cos:ListParts",
                ////                "name/cos:UploadPart",
                ////                "name/cos:CompleteMultipartUpload"
                "name/cos:*"};
        config.put("allowActions", allowActions);
        JSONObject credential = CosStsClient.getCredential(config);
        if (credential == null) {
            throw new IOException("get sts return null");
        }
        if (!credential.has("credentials")) {
            throw new IOException("get sts response not contain credentials");
        }
        JSONObject credJsonObject = null;

        try {
            credJsonObject = credential.getJSONObject("credentials");
        } catch (JSONException e) {
            throw new IOException("credentials is not a json object");
        }

        if (!credJsonObject.has("tmpSecretId")) {
            throw new IOException("credentials does not contain tmpSecretId");
        }

        if (!credJsonObject.has("tmpSecretKey")) {
            throw new IOException("credentials does not contain tmpSecretKey");
        }

        if (!credJsonObject.has("sessionToken")) {
            throw new IOException("credentials does not contain sessionToken");
        }

        GetSTSResponse stsResponse = new GetSTSResponse();
        stsResponse.setTempAK(credJsonObject.getString("tmpSecretId"));
        stsResponse.setTempSK(credJsonObject.getString("tmpSecretKey"));
        stsResponse.setTempToken(credJsonObject.getString("sessionToken"));
        return stsResponse;
    }
}
