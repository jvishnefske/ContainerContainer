package org.artifactory.aol.store.response;

/**
 * @author Alexei Vainshtein
 */
public abstract class StoreResponse {

    protected int status;
    protected int result;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public abstract String getResponseMessage();

    public abstract void setResponseMessage(String message);
}
