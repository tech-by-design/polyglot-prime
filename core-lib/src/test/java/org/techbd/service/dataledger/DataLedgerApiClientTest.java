package org.techbd.service.dataledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.techbd.corelib.config.CoreAppConfig;
import org.techbd.corelib.config.CoreUdiPrimeJpaConfig;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient.Action;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient.Actor;
import org.techbd.corelib.service.dataledger.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;

class DataLedgerApiClientTest {

    @Mock
    private CoreAppConfig appConfig;

    @Mock
    private CoreUdiPrimeJpaConfig coreUdiPrimeJpaConfig;

    private static MockedStatic<HttpClient> mockedHttpClient;
    private static HttpClient httpClient;
    private DataLedgerApiClient coreDataLedgerApiClient;
    private static AppLogger appLogger;
    private static TemplateLogger templateLogger;

    @BeforeAll
    static void init() {
        mockedHttpClient = mockStatic(HttpClient.class);
        httpClient = mock(HttpClient.class);
        appLogger = mock(AppLogger.class);
        templateLogger = mock(TemplateLogger.class);
        when(appLogger.getLogger(DataLedgerApiClient.class)).thenReturn(templateLogger);
        mockedHttpClient.when(HttpClient::newHttpClient).thenReturn(httpClient);
    }

    @AfterAll
    static void close() {
        mockedHttpClient.close();
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        coreDataLedgerApiClient = new DataLedgerApiClient(appConfig,coreUdiPrimeJpaConfig,appLogger);
        when(mock(org.jooq.DSLContext.class).configuration()).thenReturn(mock(org.jooq.Configuration.class));
    }

    @Test
    void testProcessRequest_Success_WithActionReceived() throws Exception {
        String actor = "TENANT_ID";
        String action = Action.RECEIVED.getValue();
        String destination = Actor.TECHBD.getValue();
        String dataId = "testDataId";
        String interactionId = "testInteractionId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);
        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
       
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

    @Test
    void testProcessRequest_Success_WithActionSent() throws Exception {
        String actor = Actor.TECHBD.getValue();
        String action = Action.SENT.getValue();
        String destination = Actor.NYEC.getValue();
        String dataId = "testDataId";
        String interactionId = "testInteractionId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);

        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        // when(appConfig.isDataLedgerTracking()).thenReturn(true);
        // when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

    @Test
    void testProcessRequest_Success_WithDataLedgerTracking() throws Exception {
        String actor = "TENANT_ID";
        String action = Action.RECEIVED.getValue();
        String destination = Actor.TECHBD.getValue();
        String dataId = "testDataId";
        String interactionId = "testInteractionId";
        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);
        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

    @Test
    void testProcessRequest_Success_WithDataLedgerTrackingTrueAndDiagnosticsFalse() throws Exception {
        String actor = "testActor";
        String action = "testAction";
        String destination = "testDestination";
        String dataId = "testDataId";
        String interactionId = "testInteractionId";
        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);
        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
         @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");
        
        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, false);
    }

    @Test
    void testProcessRequest_Success_WithoutDataLedgerTracking() throws Exception {
        String actor = "testActor";
        String action = "testAction";
        String destination = "testDestination";
        String dataId = "testDataId";
        String interactionId = "testInteractionId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);

        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

    @Test
    void testProcessRequest_Failure() throws Exception {
        // Setup the same test data
        String actor = "testActor";
        String action = "testAction";
        String destination = "testDestination";
        String dataId = "testDataId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);

        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        // Mock failure response
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Error");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

    @Test
    void testProcessRequest_ThrowsIOException() throws Exception {
        String actor = "testActor";
        String action = "testAction";
        String destination = "testDestination";
        String dataId = "testDataId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);
        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        
        CompletableFuture<HttpResponse<String>> mockFuture = new CompletableFuture<>();
        mockFuture.completeExceptionally(new IOException("Mocked IOException"));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        coreDataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData, true, true);
    }

}
