package org.artifactory.jfbus;

class DisabledUemV2Publisher implements UemV2Publisher {

    @Override
    public void publish(UemV2PublishRequest request) {
        //noop
    }

    @Override
    public void createTopic(String entityType, int partitions) {
        //noop
    }
}
