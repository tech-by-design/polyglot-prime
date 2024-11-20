package org.techbd.model;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CodeSystem {
    private String code;
    private String system;
    private String display;

    // Getters and setters

    @JsonProperty("code")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @JsonProperty("system")
    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    @JsonProperty("display")
    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }
}
