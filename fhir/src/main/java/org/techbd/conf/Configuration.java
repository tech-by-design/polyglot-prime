package org.techbd.conf;

/**
 * Encapsulates configuration that is common across all Technology by Design
 * (TechBD) packages.
 * 
 * Configurations specific to particular modules or packages will be in
 * submodules.
 */
public class Configuration {
    public class Servlet {
        public class HeaderName {
            public static final String PREFIX = "X-TechBD-";

            public class Request {
                public static final String TENANT_ID = PREFIX + "Tenant-ID";
                public static final String TENANT_NAME = PREFIX + "Tenant-Name";
            }
    
            public class Response {
                // in case they're necessary
            }    
        }
    }
}
