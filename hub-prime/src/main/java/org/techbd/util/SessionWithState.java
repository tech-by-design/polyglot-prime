package org.techbd.util;

public class SessionWithState {
    public String getHubSessionId() {
        return hubSessionId;
    }

    public void setHubSessionId(String hubSessionId) {
        this.hubSessionId = hubSessionId;
    }

    public String getHubSessionEntryId() {
        return hubSessionEntryId;
    }

    public void setHubSessionEntryId(String hubSessionEntryId) {
        this.hubSessionEntryId = hubSessionEntryId;
    }

    private String hubSessionId;
    private String hubSessionEntryId;
}