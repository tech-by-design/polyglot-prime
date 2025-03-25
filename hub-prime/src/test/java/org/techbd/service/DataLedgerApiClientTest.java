package org.techbd.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.techbd.service.DataLedgerApiClient.Action;
import org.techbd.service.DataLedgerApiClient.Actor;
import org.techbd.service.DataLedgerApiClient.DataLedgerPayload;
import org.techbd.udi.UdiPrimeJpaConfig;

class DataLedgerApiClientTest {

    @Mock
    private org.techbd.service.http.hub.prime.AppConfig appConfig;

    @Mock
    private HttpClient httpClient;
    @Mock
    private UdiPrimeJpaConfig udiPrimeJpaConfig;
    @InjectMocks
    private DataLedgerApiClient dataLedgerApiClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(udiPrimeJpaConfig.dsl()).thenReturn(mock(org.jooq.DSLContext.class));
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
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any());
        verify(udiPrimeJpaConfig, times(1)).dsl();
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
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any());
        verify(udiPrimeJpaConfig, times(1)).dsl();
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
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any());
        verify(udiPrimeJpaConfig, times(1)).dsl();
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
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(false);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any());
        verify(udiPrimeJpaConfig, times(0)).dsl();
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
        when(appConfig.isDataLedgerTracking()).thenReturn(false);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Success");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
        verify(httpClient, times(0)).sendAsync(any(HttpRequest.class), any());
        verify(udiPrimeJpaConfig, times(0)).dsl();
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
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        // Mock failure response
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Error");

        CompletableFuture<HttpResponse<String>> mockFuture = CompletableFuture.completedFuture(mockResponse);
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);

        verify(httpClient, times(1)).sendAsync(any(HttpRequest.class), any());

    }

    @Test
    void testProcessRequest_ThrowsIOException() throws Exception {
        String actor = "testActor";
        String action = "testAction";
        String destination = "testDestination";
        String dataId = "testDataId";

        DataLedgerPayload payload = DataLedgerPayload.create(actor, action, destination, dataId);

        when(appConfig.getDataLedgerApiUrl()).thenReturn("http://mock-endpoint");
        when(appConfig.isDataLedgerTracking()).thenReturn(true);
        when(appConfig.isDataLedgerDiagnostics()).thenReturn(true);

        CompletableFuture<HttpResponse<String>> mockFuture = new CompletableFuture<>();
        mockFuture.completeExceptionally(new IOException("Mocked IOException"));
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockFuture);

        Map<String, Object> additionalData = new HashMap<>();
        dataLedgerApiClient.processRequest(payload, actor, action, destination, additionalData);
    }

}
