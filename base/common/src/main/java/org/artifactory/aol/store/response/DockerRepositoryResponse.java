/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.aol.store.response;

import java.io.Serializable;


/**
 * @author Nessim
 */
public class DockerRepositoryResponse extends StoreResponse implements Serializable {

    private String message;

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String getResponseMessage() {
        return message;
    }

    @Override
    public void setResponseMessage(String message) {
        setMessage(message);
    }
}
