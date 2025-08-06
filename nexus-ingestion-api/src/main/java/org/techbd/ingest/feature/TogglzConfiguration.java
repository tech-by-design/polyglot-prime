package org.techbd.ingest.feature;

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
            //can customize this to get the user from the request

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