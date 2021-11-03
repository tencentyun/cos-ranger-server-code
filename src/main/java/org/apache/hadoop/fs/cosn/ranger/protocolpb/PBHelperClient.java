package org.apache.hadoop.fs.cosn.ranger.protocolpb;

import com.google.protobuf.ByteString;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQCloudObjectStorageProtocolProtos;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.AccessType;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.PermissionRequest;
import org.apache.hadoop.fs.cosn.ranger.security.authorization.ServiceType;
import org.apache.hadoop.fs.cosn.ranger.security.sts.GetSTSRequest;
import org.apache.hadoop.fs.cosn.ranger.security.token.DelegationTokenIdentifier;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.proto.SecurityProtos.TokenProto;
import org.apache.hadoop.security.token.Token;

import java.util.concurrent.ConcurrentHashMap;

public class PBHelperClient {
    /**
     * Map used to cache fixed strings to ByteStrings. Since there is no
     * automatic expiration policy, only use this for strings from a fixed, small
     * set.
     * <p/>
     * This map should not be accessed directly. Used the getFixedByteString
     * methods instead.
     */
    private static final ConcurrentHashMap<Object, ByteString> FIXED_BYTESTRING_CACHE = new ConcurrentHashMap<>();

    public static Token<DelegationTokenIdentifier> convertDelegationToken(TokenProto blockToken) {
        return new Token<>(blockToken.getIdentifier().toByteArray(), blockToken.getPassword().toByteArray(),
                new Text(blockToken.getKind()), new Text(blockToken.getService()));
    }

    public static TokenProto convert(Token<?> tok) {
        TokenProto.Builder builder = TokenProto.newBuilder().setIdentifier(getByteString(tok.getIdentifier()))
                                               .setPassword(getByteString(tok.getPassword())).setKindBytes(
                        getFixedByteString(tok.getKind())).setServiceBytes(getFixedByteString(tok.getService()));
        return builder.build();
    }

    public static ByteString getByteString(byte[] bytes) {
        // return singleton to reduce object allocation
        return (bytes.length == 0) ? ByteString.EMPTY : ByteString.copyFrom(bytes);
    }

    /**
     * Get the ByteString for frequently used fixed and small set strings.
     *
     * @param key string
     * @return
     */
    public static ByteString getFixedByteString(Text key) {
        ByteString value = FIXED_BYTESTRING_CACHE.get(key);
        if (value == null) {
            value = ByteString.copyFromUtf8(key.toString());
            FIXED_BYTESTRING_CACHE.put(new Text(key.copyBytes()), value);
        }
        return value;
    }

    public static PermissionRequest convertPermissionRequest(
            ClientQCloudObjectStorageProtocolProtos.CheckPermissionRequest permissionReqProto) {
        return new PermissionRequest(ServiceType.valueOf(permissionReqProto.getServiceType().name()),
                AccessType.valueOf(permissionReqProto.getAccessType().name()), permissionReqProto.getBucketName(),
                permissionReqProto.getObjectKey(), permissionReqProto.getFsMountPoint(),
                permissionReqProto.getChdfsPath());
    }

    public static GetSTSRequest convertGetSTSRequest(
            ClientQCloudObjectStorageProtocolProtos.GetSTSRequest getSTSRequestProto) {
        GetSTSRequest req = new GetSTSRequest();
        req.setBucketName(getSTSRequestProto.getBucketName());
        req.setAllowPrefix(getSTSRequestProto.getAllowPrefix());
        req.setBucketRegion(getSTSRequestProto.getBucketRegion());
        return req;
    }
}
