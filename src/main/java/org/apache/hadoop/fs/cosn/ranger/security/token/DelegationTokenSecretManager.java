package org.apache.hadoop.fs.cosn.ranger.security.token;

import org.apache.hadoop.security.token.delegation.AbstractDelegationTokenSecretManager;
import org.apache.hadoop.security.token.delegation.DelegationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;

public class DelegationTokenSecretManager extends AbstractDelegationTokenSecretManager<DelegationTokenIdentifier> {
    private static final Logger log = LoggerFactory.getLogger(DelegationTokenSecretManager.class);

    public DelegationTokenSecretManager(long delegationKeyUpdateInterval, long delegationTokenMaxLifetime,
            long delegationTokenRenewInterval, long delegationTokenRemoverScanInterval) {
        super(delegationKeyUpdateInterval, delegationTokenMaxLifetime, delegationTokenRenewInterval,
                delegationTokenRemoverScanInterval);
    }

    @Override
    public DelegationTokenIdentifier createIdentifier() {
        return new DelegationTokenIdentifier();
    }

    @Override
    protected void logUpdateMasterKey(DelegationKey key) throws IOException {
        return;
    }

    @Override
    protected void logExpireToken(DelegationTokenIdentifier ident) throws IOException {
        return;
    }

    @Override
    protected void storeNewMasterKey(DelegationKey key) throws IOException {
        return;
    }

    @Override
    protected void removeStoredMasterKey(DelegationKey key) {
        return;
    }

    @Override
    protected void storeNewToken(DelegationTokenIdentifier ident, long renewDate) throws IOException {
        return;
    }

    @Override
    protected void removeStoredToken(DelegationTokenIdentifier ident) throws IOException {

    }

    @Override
    protected void updateStoredToken(DelegationTokenIdentifier ident, long renewDate) throws IOException {
        return;
    }

    public synchronized long getTokenExpiryTime(DelegationTokenIdentifier dtId) throws IOException {
        DelegationTokenInformation info = currentTokens.get(dtId);
        if (info != null) {
            return info.getRenewDate();
        } else {
            throw new IOException("No delegation token found for this identifier");
        }
    }

    public synchronized void load(DataInput in) throws IOException {
        currentId = in.readInt();
        loadAllKeys(in);
        delegationTokenSequenceNumber = in.readInt();
        loadCurrentTokens(in);
    }

    private void loadAllKeys(DataInput in) throws IOException {
        int numberOfKeys = in.readInt();
        for (int i = 0; i < numberOfKeys; i++) {
            DelegationKey value = new DelegationKey();
            value.readFields(in);
            addKey(value);
        }
    }

    private void loadCurrentTokens(DataInput in) throws IOException {
        int numberOfTokens = in.readInt();
        for (int i = 0; i < numberOfTokens; i++) {
            DelegationTokenIdentifier id = new DelegationTokenIdentifier();
            id.readFields(in);
            long expiryTime = in.readLong();
            addPersistedDelegationToken(id, expiryTime);
        }
    }

    /**
     * This method is intended to be used only while reading edit logs.
     *
     * @param identifier DelegationTokenIdentifier
     * @param expiryTime token expiry time
     * @throws IOException
     */
    public synchronized void addPersistedDelegationToken(DelegationTokenIdentifier identifier, long expiryTime)
            throws IOException {
        if (running) {
            // a safety check
            throw new IOException("Can't add persisted delegation token to a running SecretManager.");
        }
        int keyId = identifier.getMasterKeyId();
        DelegationKey dKey = allKeys.get(keyId);
        if (dKey == null) {
            log.warn("No KEY found for persisted identifier " + identifier.toString());
            return;
        }
        byte[] password = createPassword(identifier.getBytes(), dKey.getKey());
        if (identifier.getSequenceNumber() > this.delegationTokenSequenceNumber) {
            this.delegationTokenSequenceNumber = identifier.getSequenceNumber();
        }
        if (currentTokens.get(identifier) == null) {
            currentTokens.put(identifier,
                    new DelegationTokenInformation(expiryTime, password, getTrackingIdIfEnabled(identifier)));
        } else {
            throw new IOException("Same delegation token being added twice; invalid entry in fsimage or editlogs");
        }
    }

    public synchronized void save(DataOutputStream out) throws IOException {
        out.writeInt(currentId);
        saveAllKeys(out);
        out.writeInt(delegationTokenSequenceNumber);
        saveCurrentTokens(out);
    }

    /*
     * Save the current state of allKeys
     */
    private void saveAllKeys(DataOutputStream out) throws IOException {
        out.writeInt(allKeys.size());
        Iterator<Integer> iter = allKeys.keySet().iterator();
        while (iter.hasNext()) {
            Integer key = iter.next();
            allKeys.get(key).write(out);
        }
    }

    /**
     * Private helper methods to save delegation keys and tokens in fsimage
     */
    private void saveCurrentTokens(DataOutputStream out) throws IOException {
        out.writeInt(currentTokens.size());
        Iterator<DelegationTokenIdentifier> iter = currentTokens.keySet().iterator();
        while (iter.hasNext()) {
            DelegationTokenIdentifier id = iter.next();
            id.write(out);
            DelegationTokenInformation info = currentTokens.get(id);
            out.writeLong(info.getRenewDate());
        }
    }

}

