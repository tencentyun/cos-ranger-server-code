package org.apache.hadoop.fs.cosn.ranger.protocolpb;

import org.apache.hadoop.fs.cosn.ranger.constants.ObjectStorageConstants;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQCloudObjectStorageProtocolProtos;
import org.apache.hadoop.fs.cosn.ranger.security.token.DelegationTokenSelector;
import org.apache.hadoop.ipc.ProtocolInfo;
import org.apache.hadoop.ipc.VersionedProtocol;
import org.apache.hadoop.security.KerberosInfo;
import org.apache.hadoop.security.token.TokenInfo;

@KerberosInfo(serverPrincipal = ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_KERBEROS_PRINCIPAL_KEY)
@TokenInfo(DelegationTokenSelector.class)
@ProtocolInfo(protocolName = ObjectStorageConstants.QCLOUD_OBJECT_STORAGE_PROTOCOL_NAME, protocolVersion = 1)
public interface ClientQcloudObjectStorageProtocolPB
        extends ClientQCloudObjectStorageProtocolProtos.ClientQcloudObjectStorageProtocol.BlockingInterface,
                VersionedProtocol {
}
