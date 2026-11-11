package org.artifactory.mirror;

public enum DeleteEventHintType {

    File("file:true"),
    Folder("file:false");

    private final String description;

    DeleteEventHintType(String description){
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

}
