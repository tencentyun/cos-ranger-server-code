package org.apache.hadoop.fs.cosn.ranger.protocolpb.server;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQCloudObjectStorageProtocolProtos;
import org.apache.hadoop.fs.cosn.ranger.protocol.ClientQcloudObjectStorageProtocol;
import org.apache.hadoop.fs.cosn.ranger.protocolpb.ClientQcloudObjectStorageProtocolPB;
import org.apache.hadoop.fs.cosn.ranger.protocolpb.PBHelperClient;
import org.apache.hadoop.fs.cosn.ranger.security.sts.GetSTSResponse;
import org.apache.hadoop.fs.cosn.ranger.security.token.DelegationTokenIdentifier;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.ipc.ProtocolSignature;
import org.apache.hadoop.ipc.RPC;
import org.apache.hadoop.security.proto.SecurityProtos.CancelDelegationTokenRequestProto;
import org.apache.hadoop.security.proto.SecurityProtos.CancelDelegationTokenResponseProto;
import org.apache.hadoop.security.proto.SecurityProtos.GetDelegationTokenRequestProto;
import org.apache.hadoop.security.proto.SecurityProtos.GetDelegationTokenResponseProto;
import org.apache.hadoop.security.proto.SecurityProtos.RenewDelegationTokenRequestProto;
import org.apache.hadoop.security.proto.SecurityProtos.RenewDelegationTokenResponseProto;
import org.apache.hadoop.security.token.Token;

import java.io.IOException;

public class ClientQcloudObjectStorageProtocolServerSideTranslatorPB implements ClientQcloudObjectStorageProtocolPB {
    private static final CancelDelegationTokenResponseProto VOID_CANCELDELEGATIONTOKEN_RESPONSE =
            CancelDelegationTokenResponseProto.newBuilder().build();
    private final ClientQcloudObjectStorageProtocol server;

    public ClientQcloudObjectStorageProtocolServerSideTranslatorPB(ClientQcloudObjectStorageProtocol server) {
        this.server = server;
    }


    @Override
    public GetDelegationTokenResponseProto getDelegationToken(RpcController controller,
            GetDelegationTokenRequestProto request) throws ServiceException {
        try {
            Token<DelegationTokenIdentifier> token = server.getDelegationToken(new Text(request.getRenewer()));
            GetDelegationTokenResponseProto.Builder rspBuilder = GetDelegationTokenResponseProto.newBuilder();
            if (token != null) {
                rspBuilder.setToken(PBHelperClient.convert(token));
            }
            return rspBuilder.build();
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public RenewDelegationTokenResponseProto renewDelegationToken(RpcController controller,
            RenewDelegationTokenRequestProto request) throws ServiceException {
        try {
            long result = server.renewDelegationToken(PBHelperClient.convertDelegationToken(request.getToken()));
            return RenewDelegationTokenResponseProto.newBuilder().setNewExpiryTime(result).build();
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public CancelDelegationTokenResponseProto cancelDelegationToken(RpcController controller,
            CancelDelegationTokenRequestProto request) throws ServiceException {
        try {
            server.cancelDelegationToken(PBHelperClient.convertDelegationToken(request.getToken()));
            return VOID_CANCELDELEGATIONTOKEN_RESPONSE;
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public long getProtocolVersion(String protocol, long clientVersion) throws IOException {
        return RPC.getProtocolVersion(ClientQcloudObjectStorageProtocolPB.class);
    }

    @Override
    public ProtocolSignature getProtocolSignature(String protocol, long clientVersion, int clientMethodsHash)
            throws IOException {
        if (!protocol.equals(RPC.getProtocolName(ClientQcloudObjectStorageProtocolPB.class))) {
            throw new IOException(
                    String.format("ServerSide implements %s. The following requested protocol is unknown: %s",
                    RPC.getProtocolName(ClientQcloudObjectStorageProtocolPB.class),
                            protocol));
        }

        return ProtocolSignature.getProtocolSignature(clientMethodsHash,
                RPC.getProtocolVersion(ClientQcloudObjectStorageProtocolPB.class),
                ClientQcloudObjectStorageProtocolPB.class);
    }

    @Override
    public ClientQCloudObjectStorageProtocolProtos.CheckPermissionResponse checkPermission(RpcController controller,
            ClientQCloudObjectStorageProtocolProtos.CheckPermissionRequest request) throws ServiceException {
        try {
            boolean isAllowed = server.checkPermission(PBHelperClient.convertPermissionRequest(request));
            return ClientQCloudObjectStorageProtocolProtos.CheckPermissionResponse.newBuilder().setAllowed(isAllowed)
                                                                                  .build();
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public ClientQCloudObjectStorageProtocolProtos.GetSTSResponse getSTS(RpcController controller,
            ClientQCloudObjectStorageProtocolProtos.GetSTSRequest request) throws ServiceException {
        try {
            GetSTSResponse stsResponse = server.getSTS(PBHelperClient.convertGetSTSRequest(request));
            return ClientQCloudObjectStorageProtocolProtos.GetSTSResponse.newBuilder().setTempAK(
                    stsResponse.getTempAK()).setTempSK(stsResponse.getTempSK()).setTempToken(stsResponse.getTempToken())
                                                                         .build();
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }

    @Override
    public ClientQCloudObjectStorageProtocolProtos.GetRangerPolicyUrlResponse getRangerPolicyUrl(
            RpcController controller,
            ClientQCloudObjectStorageProtocolProtos.GetRangerPolicyUrlRequest request)
            throws ServiceException {
        try {
            String policyUrl = server.getRangerPolicyUrl();
            return ClientQCloudObjectStorageProtocolProtos
                    .GetRangerPolicyUrlResponse
                    .newBuilder().setRangerPolicyUrl(policyUrl)
                    .build();
        } catch (IOException e) {
            throw new ServiceException(e);
        }
    }
}
