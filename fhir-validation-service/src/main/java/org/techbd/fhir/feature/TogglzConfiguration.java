package org.techbd.fhir.feature;

import org.springframework.context.annotation.Bean; 
import org.springframework.context.annotation.Configuration; 
import org.togglz.core.user.FeatureUser; 
import org.togglz.core.user.UserProvider;   

@Configuration
public class TogglzConfiguration {
 
    public TogglzConfiguration() {   
    } 
 
    @Bean 
    public UserProvider userProvider() { 
        return new UserProvider() { 
            @Override 
            public FeatureUser getCurrentUser() { 
                return new FeatureUser() { 
 
                    @Override 
                    public String getName() { 
                        return "anonymous"; 
                    } 
 
                    @Override 
                    public boolean isFeatureAdmin() { 
                        return true; 
                    } 
 
                    @Override 
                    public String getAttribute(String name) { 
                        return null; 
                    } 
 
                }; 
            } 
        }; 
    }
}