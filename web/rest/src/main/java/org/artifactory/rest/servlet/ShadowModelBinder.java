package org.artifactory.rest.servlet;

import org.glassfish.jersey.inject.hk2.AbstractBinder;
import org.glassfish.jersey.server.model.ModelProcessor;

/**
 * Using abstract binder so that the model processor will get called after
 * {@link org.artifactory.rest.jersey.RemoveOptionsModelProcessor}
 * For model processors registered using binders see ModelProcessorConfigurator
 */
public class ShadowModelBinder extends AbstractBinder {

    ShadowModelProcessor modelProcessor;

    public ShadowModelBinder() {
        this(new ShadowModelProcessor());
    }

    ShadowModelBinder(ShadowModelProcessor modelProcessor) {
        this.modelProcessor = modelProcessor;
    }

    @Override
    protected void configure() {
        bind(modelProcessor).to(ModelProcessor.class);
    }
}
