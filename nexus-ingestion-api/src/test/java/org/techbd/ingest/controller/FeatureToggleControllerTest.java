package org.techbd.ingest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.techbd.ingest.feature.FeatureEnum;
import org.togglz.core.manager.FeatureManager;
import org.togglz.core.repository.StateRepository;
import org.junit.jupiter.api.Test;
import org.togglz.core.repository.FeatureState;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FeatureToggleControllerTest {

    @Mock
    private FeatureManager featureManager;

    @Mock
    private StateRepository stateRepository;

    @InjectMocks
    private FeatureToggleController featureToggleController;

    private FeatureEnum feature;

    @BeforeEach
    void setUp() {
        feature = FeatureEnum.values()[0];
    }

    @Test
    void enableFeature_shouldEnableSuccessfully() {

        ResponseEntity<Map<String, Object>> response = featureToggleController.enableFeature(feature.name());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("enabled", response.getBody().get("status"));

        verify(featureManager).setFeatureState(any(FeatureState.class));
    }

    @Test
    void disableFeature_shouldDisableSuccessfully() {
        ResponseEntity<Map<String, Object>> response = featureToggleController.disableFeature(feature.name());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("disabled", response.getBody().get("status"));

        verify(featureManager).setFeatureState(new FeatureState(feature, false));
    }

    @Test
    void isFeatureActive_shouldReturnEnabled() {
        when(featureManager.isActive(feature)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = featureToggleController.isFeatureActive(feature.name());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("enabled"));
        assertEquals("enabled", response.getBody().get("status"));
    }

    @Test
    void toggleFeature_shouldSwitchState() {
        when(featureManager.isActive(feature)).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = featureToggleController.toggleFeature(feature.name());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("enabled", response.getBody().get("currentStatus"));

        verify(featureManager).setFeatureState(new FeatureState(feature, true));
    }

    @Test
    void getFeatureSummary_shouldReturnAllFeatures() {
        for (FeatureEnum f : FeatureEnum.values()) {
            when(featureManager.isActive(f)).thenReturn(true);
        }

        ResponseEntity<Map<String, String>> response = featureToggleController.getFeatureSummary();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("enabled", response.getBody().get(feature.name()));
    }

    @Test
    void getFeatureNames_shouldReturnNamesList() {
        ResponseEntity<Map<String, Object>> response = featureToggleController.getFeatureNames();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(FeatureEnum.values().length, response.getBody().get("count"));
    }

    @Test
    void resetFeature_shouldReturnDefaultState() {
        when(featureManager.isActive(feature)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = featureToggleController.resetFeature(feature.name());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Feature reset to enum default", response.getBody().get("message"));

        verify(stateRepository).setFeatureState(null);
    }

    @Test
    void resetAllFeatures_shouldResetAll() throws Exception {
        ResponseEntity<Map<String, Object>> response = featureToggleController.resetAllFeatures();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("All features reset to default", response.getBody().get("message"));

        verify(featureManager, atLeastOnce()).setFeatureState(any(FeatureState.class));
    }

    @Test
    void invalidFeature_shouldReturnBadRequest() {
        ResponseEntity<Map<String, Object>> response = featureToggleController.enableFeature("INVALID_FEATURE");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void disableFeature_shouldReturnBadRequest_whenInvalidFeatureName() {

        String invalidFeature = "INVALID_FEATURE";

        ResponseEntity<Map<String, Object>> response = featureToggleController.disableFeature(invalidFeature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Feature does not exist", response.getBody().get("message"));
        assertTrue(response.getBody().get("error")
                .toString()
                .contains("Invalid feature name"));

        verify(featureManager, never()).setFeatureState(any());
    }

    @Test
    void isFeatureActive_shouldReturnBadRequest_whenInvalidFeatureName() {

        String invalidFeature = "INVALID_FEATURE";

        ResponseEntity<Map<String, Object>> response = featureToggleController.isFeatureActive(invalidFeature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Feature does not exist", response.getBody().get("message"));
        assertTrue(response.getBody().get("error")
                .toString()
                .contains("Invalid feature name"));

        verify(featureManager, never()).isActive(any());
    }

    @Test
    void toggleFeature_shouldReturnBadRequest_whenInvalidFeatureName() {

        String invalidFeature = "INVALID_FEATURE";

        ResponseEntity<Map<String, Object>> response = featureToggleController.toggleFeature(invalidFeature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Feature does not exist", response.getBody().get("message"));
        assertTrue(response.getBody().get("error")
                .toString()
                .contains("Invalid feature name"));

        verify(featureManager, never()).isActive(any());
        verify(featureManager, never()).setFeatureState(any());
    }

    @Test
    void resetFeature_shouldReturnBadRequest_whenInvalidFeatureName() {

        String invalidFeature = "INVALID_FEATURE";

        ResponseEntity<Map<String, Object>> response = featureToggleController.resetFeature(invalidFeature);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error")
                .toString()
                .contains("Invalid feature name"));

        verify(stateRepository, never()).setFeatureState(any());
        verify(featureManager, never()).isActive(any());
    }
}