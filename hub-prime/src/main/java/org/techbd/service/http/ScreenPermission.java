package org.techbd.service.http;

import java.util.List;

public record ScreenPermission(
        String screen,
        List<String> children
) {}