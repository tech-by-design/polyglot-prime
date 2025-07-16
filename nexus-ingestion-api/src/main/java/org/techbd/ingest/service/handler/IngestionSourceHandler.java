package org.techbd.ingest.service.handler;

import java.util.Map;

import org.techbd.ingest.model.RequestContext;

/**
 * {@code IngestionSourceHandler} is an interface that defines the contract for handling different types
 * of source objects during the ingestion process.
 * <p>
 * Implementations are responsible for:
 * <ul>
 *   <li>Determining whether they can handle a given source via {@link #canHandle(Object)}</li>
 *   <li>Performing the actual processing of the source via {@link #handleAndProcess(Object, RequestContext)}</li>
 * </ul>
 * This enables a flexible and extensible ingestion pipeline capable of supporting various input types
 * such as files, HL7 messages, JSON payloads, etc.
 * </p>
 *
 * <p><b>To add a new handler:</b></p>
 * <ol>
 *   <li>Create a new class that implements {@code IngestionSourceHandler}.</li>
 *   <li>Implement the {@code canHandle(Object)} method to return {@code true} for the specific type you want to support.</li>
 *   <li>Implement the {@code handleAndProcess(Object, RequestContext)} method to define the processing logic.</li>
 *   <li>Annotate the class with {@code @Component} (or register it as a Spring bean).</li>
 *   <li>Ensure it is picked up via component scanning so that {@code IngestionRouter} receives it in the list of handlers.</li>
 * </ol>
 */
public interface IngestionSourceHandler {
    boolean canHandle(Object source);
    Map<String, String> handleAndProcess(Object source, RequestContext context);
}

