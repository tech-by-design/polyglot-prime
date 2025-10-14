package org.techbd.ingest.service;

import org.techbd.ingest.model.RequestContext;

public interface MessageGroupStrategy {
    boolean supports(RequestContext context);
    String createGroupId(RequestContext context, String interactionId);
}

