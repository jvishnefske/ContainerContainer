package org.artifactory.aol.store.response;

import java.io.Serializable;

/**
 * @author Alexei Vainshtein
 */
public class PurchaseUrlResponse extends StoreResponse implements Serializable {

    private String purchaseUrl;

    private void setPurchaseUrl(String purchaseUrl) {
        this.purchaseUrl = purchaseUrl;
    }

    @Override
    public String getResponseMessage() {
        return purchaseUrl;
    }

    @Override
    public void setResponseMessage(String message) {
        setPurchaseUrl(message);
    }
}
