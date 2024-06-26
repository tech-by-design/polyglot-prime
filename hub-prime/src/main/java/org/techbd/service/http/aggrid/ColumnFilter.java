package org.techbd.service.http.aggrid;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "filterType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = NumberColumnFilter.class, name = "number"),
    @JsonSubTypes.Type(value = SetColumnFilter.class, name = "set"),
    @JsonSubTypes.Type(value = TextColumnFilter.class, name = "text")})
public abstract class ColumnFilter {

    String filterType;
}
