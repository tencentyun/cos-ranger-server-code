package org.apache.hadoop.fs.cosn.ranger.security.token;

import org.apache.hadoop.security.token.delegation.AbstractDelegationTokenSelector;

public class DelegationTokenSelector extends AbstractDelegationTokenSelector<DelegationTokenIdentifier> {

    public DelegationTokenSelector() {
        super(DelegationTokenIdentifier.QCLOUD_OBJECT_STORAGE_DELEGATION_KIND);
    }
}