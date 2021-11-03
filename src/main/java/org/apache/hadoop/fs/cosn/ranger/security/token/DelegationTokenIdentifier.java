package org.apache.hadoop.fs.cosn.ranger.security.token;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.delegation.AbstractDelegationTokenIdentifier;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class DelegationTokenIdentifier extends AbstractDelegationTokenIdentifier {
    public static final Text QCLOUD_OBJECT_STORAGE_DELEGATION_KIND = new Text("QCLOUD_OBJECT_STORAGE_DELEGATION_TOKEN");

    /**
     * Create an empty delegation token identifier for reading into.
     */
    public DelegationTokenIdentifier() {
    }

    /**
     * Create a new delegation token identifier
     *
     * @param owner    the effective username of the token owner
     * @param renewer  the username of the renewer
     * @param realUser the real username of the token owner
     */
    public DelegationTokenIdentifier(Text owner, Text renewer, Text realUser) {
        super(owner, renewer, realUser);
    }

    public static DelegationTokenIdentifier decodeDelegationToken(final Token<DelegationTokenIdentifier> token)
            throws IOException {
        final DelegationTokenIdentifier id = new DelegationTokenIdentifier();
        final ByteArrayInputStream buf = new ByteArrayInputStream(token.getIdentifier());
        try (DataInputStream in = new DataInputStream(buf)) {
            id.readFields(in);
        }
        return id;
    }

    @Override
    public Text getKind() {
        return QCLOUD_OBJECT_STORAGE_DELEGATION_KIND;
    }
}