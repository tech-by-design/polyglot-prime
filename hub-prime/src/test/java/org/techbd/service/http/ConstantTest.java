package org.techbd.service.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ConstantTest {

    @Test
    void testIsStatelessApiUrl_ContainsStatelessPattern() {
        String requestUrl = "https://example.com/Bundle";
        boolean result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/Bundle/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/Bundle/$validate";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/Bundle/$validate/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/flatfile/csv/Bundle";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/flatfile/csv/Bundle/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/Hl7/v2";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/Hl7/v2/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");

        requestUrl = "https://example.com/api/expect/fhir/bundle";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertTrue(result, "The URL must match a stateless API pattern.");
    }

    @Test
    void testIsStatelessApiUrl_DoesNotContainStatelessPattern() {
        String requestUrl = "https://example.com/mock/shinny-data-lake/1115-validate/resourcePath.json";
        boolean result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/api/ux/tabular/jooq/schemaName/masterTableNameOrViewName.json";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/api/ux/tabular/jooq/sp/schemaName/storedProcName.json";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/api/ux/tabular/jooq/schemaName/masterTableNameOrViewName/columnName/columnValue.json";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/api/ux/tabular/jooq/save/schemaName/tableName.json";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        // Un-authenticated URLs
        requestUrl = "https://example.com/login";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/oauth2";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/home";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/metadata";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/docs/api/interactive/swagger-ui";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/support";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/docs/api/interactive";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/docs/api/openapi";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/error";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");

        requestUrl = "https://example.com/";
        result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "The URL must not match any stateless API pattern.");
    }

    @Test
    void testIsStatelessApiUrl_EmptyRequestUrl() {
        String requestUrl = "";
        boolean result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "An empty URL must not match any stateless API pattern.");
    }

    @Test
    void testIsStatelessApiUrl_NullRequestUrl() {
        String requestUrl = null;
        boolean result = Constant.isStatelessApiUrl(requestUrl);
        assertFalse(result, "A null URL must not match any stateless API pattern.");
    }
}
