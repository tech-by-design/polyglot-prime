package org.techbd.ingest.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.techbd.ingest.commons.Constants;

public class HttpUtilTenantIdTest {

    @Test
    void testExtractTenantId_WithXTechBDTenantID() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.REQ_HEADER_TENANT_ID, "my-custom-tenant");
        
        String result = HttpUtil.extractTenantId(headers);
        
        assertThat(result).isEqualTo("my-custom-tenant");
    }

    @Test
    void testExtractTenantId_WithNoHeaders_FallsBackToDefault() {
        Map<String, String> headers = new HashMap<>();
        
        String result = HttpUtil.extractTenantId(headers);
        
        // Falls back to Constants.DEFAULT_TENANT_ID when no header and no env var
        assertThat(result).isEqualTo(Constants.DEFAULT_TENANT_ID);
    }

    @Test
    void testExtractTenantId_WithEmptyHeader_FallsBackToDefault() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.REQ_HEADER_TENANT_ID, "");
        
        String result = HttpUtil.extractTenantId(headers);
        
        assertThat(result).isEqualTo(Constants.DEFAULT_TENANT_ID);
    }

    @Test
    void testExtractTenantId_WithWhitespaceHeader_FallsBackToDefault() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.REQ_HEADER_TENANT_ID, "   ");
        
        String result = HttpUtil.extractTenantId(headers);
        
        assertThat(result).isEqualTo(Constants.DEFAULT_TENANT_ID);
    }
}