package org.techbd.ingest.processor;

import org.springframework.web.multipart.MultipartFile;
import org.techbd.ingest.model.RequestContext;
/**
 * {@code MessageProcessingStep} defines a single step in the message ingestion pipeline.
 * <p>
 * Each implementation of this interface performs a specific operation on the incoming message,
 * such as:
 * <ul>
 *   <li>Uploading the message to AWS S3</li>
 *   <li>Publishing a notification to AWS SQS</li>
 *   <li>Validating or transforming the message</li>
 * </ul>
 *
 * <p>
 * To add a new processing step:
 * <ol>
 *   <li>Implement this interface in a new class.</li>
 *   <li>Define the logic for {@code process(MultipartFile, RequestContext)} and/or {@code process(String, RequestContext)}.</li>
 *   <li>Register the implementation as a Spring bean (e.g., using {@code @Component}).</li>
 *   <li>Ensure it is injected into the list of steps used by {@code MessageProcessorService}.</li>
 * </ol>
 * </p>
 */
public interface MessageProcessingStep {
    void process(RequestContext context, MultipartFile file);
    void process(RequestContext context, String content, String ackMessage);
    boolean isEnabledFor(RequestContext context);
}
