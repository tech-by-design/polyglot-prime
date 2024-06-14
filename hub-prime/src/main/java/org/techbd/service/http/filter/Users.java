package org.techbd.service.http.filter;

import org.techbd.service.http.hub.prime.Controller;

import java.util.List;

public record Users ( List<Controller.AuthenticatedUser> users){};

