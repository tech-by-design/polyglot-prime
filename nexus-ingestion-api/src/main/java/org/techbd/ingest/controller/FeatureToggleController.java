package org.techbd.ingest.controller;

import org.techbd.ingest.feature.FeatureEnum;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.togglz.core.manager.FeatureManager;
import org.togglz.core.repository.FeatureState;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for managing feature toggles using Togglz.
 * Provides endpoints to enable, disable, check status, and list all feature flags.
 */
@RestController
@RequestMapping("/api/features")
public class FeatureToggleController {

    private final FeatureManager featureManager;

    public FeatureToggleController(FeatureManager featureManager) {
        this.featureManager = featureManager;
    }

    /**
     * Enable a specific feature flag
     * 
     * @param featureName the name of the feature to enable
     * @return success message
     */
    @PostMapping("/{featureName}/enable")
    public ResponseEntity<Map<String, Object>> enableFeature(@PathVariable String featureName) {
        try {
            FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
            featureManager.setFeatureState(new FeatureState(feature, true));
            
            Map<String, Object> response = new HashMap<>();
            response.put("feature", featureName);
            response.put("status", "enabled");
            response.put("message", featureName + " has been enabled");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid feature name: " + featureName);
            error.put("message", "Feature does not exist");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Disable a specific feature flag
     * 
     * @param featureName the name of the feature to disable
     * @return success message
     */
    @PostMapping("/{featureName}/disable")
    public ResponseEntity<Map<String, Object>> disableFeature(@PathVariable String featureName) {
        try {
            FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
            featureManager.setFeatureState(new FeatureState(feature, false));
            
            Map<String, Object> response = new HashMap<>();
            response.put("feature", featureName);
            response.put("status", "disabled");
            response.put("message", featureName + " has been disabled");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid feature name: " + featureName);
            error.put("message", "Feature does not exist");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Check if a specific feature flag is active
     * 
     * @param featureName the name of the feature to check
     * @return feature status
     */
    @GetMapping("/{featureName}")
    public ResponseEntity<Map<String, Object>> isFeatureActive(@PathVariable String featureName) {
        try {
            FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
            boolean active = featureManager.isActive(feature);
            
            Map<String, Object> response = new HashMap<>();
            response.put("feature", featureName);
            response.put("enabled", active);
            response.put("status", active ? "enabled" : "disabled");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid feature name: " + featureName);
            error.put("message", "Feature does not exist");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Get a summary of all feature statuses (simple format)
     * 
     * @return Simple map of feature names to enabled/disabled status
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> getFeatureSummary() {
        Map<String, String> summary = new HashMap<>();
        
        for (FeatureEnum feature : FeatureEnum.values()) {
            boolean isActive = featureManager.isActive(feature);
            summary.put(feature.name(), isActive ? "enabled" : "disabled");
        }
        
        return ResponseEntity.ok(summary);
    }

    /**
     * Toggle a feature (enable if disabled, disable if enabled)
     * 
     * @param featureName the name of the feature to toggle
     * @return new feature status
     */
    @PostMapping("/{featureName}/toggle")
    public ResponseEntity<Map<String, Object>> toggleFeature(@PathVariable String featureName) {
        try {
            FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
            boolean currentStatus = featureManager.isActive(feature);
            boolean newStatus = !currentStatus;
            
            featureManager.setFeatureState(new FeatureState(feature, newStatus));
            
            Map<String, Object> response = new HashMap<>();
            response.put("feature", featureName);
            response.put("previousStatus", currentStatus ? "enabled" : "disabled");
            response.put("currentStatus", newStatus ? "enabled" : "disabled");
            response.put("enabled", newStatus);
            response.put("message", featureName + " has been " + (newStatus ? "enabled" : "disabled"));
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid feature name: " + featureName);
            error.put("message", "Feature does not exist");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    /**
     * Get list of all available feature names
     * 
     * @return List of feature names
     */
    @GetMapping("/names")
    public ResponseEntity<Map<String, Object>> getFeatureNames() {
        Map<String, Object> response = new HashMap<>();
        String[] featureNames = new String[FeatureEnum.values().length];
        
        int index = 0;
        for (FeatureEnum feature : FeatureEnum.values()) {
            featureNames[index++] = feature.name();
        }
        
        response.put("features", featureNames);
        response.put("count", featureNames.length);
        
        return ResponseEntity.ok(response);
    }
}