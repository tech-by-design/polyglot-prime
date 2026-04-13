package org.techbd.ingest.integrationtests.util;


import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StreamUtils;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared fixture-loading utility for SOAP endpoint tests.
 *
 * <p>Usable as a static import from both:
 * <ul>
 *   <li>{@code SoapEndpointFixtureTest} (Mockito unit tests — no Spring context)</li>
 *   <li>{@code SoapEndpointIT} and other IT classes (Spring Boot integration tests)</li>
 * </ul>
 *
 * <p>All fixtures live under:
 * {@code src/test/resources/org/techbd/ingest/soap-test-resources/}
 *
 * <p>Usage:
 * <pre>
 * import static org.techbd.ingest.util.SoapTestFixtures.*;
 *
 * String xml = loadFixture("pix-add-request.txt");
 * </pre>
 */
public final class SoapTestFixtures {

    /** Classpath prefix for all SOAP fixture files. */
    public static final String RESOURCE_PREFIX =
            "classpath:org/techbd/ingest/soap-test-resources/";

    /**
     * File-system path to the same resources — used by tests that read
     * fixtures with {@code Files.readString()} (e.g. {@code SoapEndpointFixtureTest}).
     */
    public static final String RESOURCE_BASE =
            "src/test/resources/org/techbd/ingest/soap-test-resources/";

    private SoapTestFixtures() {}

    /**
     * Loads a named fixture from the classpath (works in both unit and
     * integration tests, and inside JARs). Prefer this over the file-system
     * variant for portability.
     */
    public static String loadFixture(String filename) throws IOException {
        String path = RESOURCE_PREFIX + filename;
        try (InputStream is = new DefaultResourceLoader()
                .getResource(path)
                .getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    /**
     * Silent variant for use inside Mockito {@code thenReturn()} / {@code when()}
     * chains where a checked exception cannot be thrown.
     */
    public static String loadFixtureQuiet(String filename) {
        try {
            return loadFixture(filename);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read fixture: " + filename, e);
        }
    }
    public static String extractXPath(String xml, String expression) {
    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); 
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document document = builder.parse(new InputSource(new StringReader(xml)));

        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.evaluate(expression, document);

    } catch (Exception e) {
        throw new RuntimeException("Failed to evaluate XPath: " + expression, e);
    }
}
}