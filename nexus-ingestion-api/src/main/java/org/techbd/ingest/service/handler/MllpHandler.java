package org.techbd.ingest.service.handler;


import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.service.MessageProcessorService;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.HL7Exception;
/**
 * {@code MllpHandler} is an implementation of {@link IngestionSourceHandler} responsible for
 * handling HL7 messages received over the Minimal Lower Layer Protocol (MLLP).
 * <p>
 * This handler verifies that the incoming source is a valid MLLP message (typically a {@link String})
 * and delegates the processing to the {@link MessageProcessorService}.
 * </p>
 */
@Component
public class MllpHandler implements IngestionSourceHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MllpHandler.class);

    private final MessageProcessorService processorService;

    public MllpHandler(MessageProcessorService processorService) {
        this.processorService = processorService;
        LOG.info("MllpHandler initialized ");
    }

    /**
     * Checks if the source object is a valid MLLP message.
     *
     * @param source The source object to check.
     * @return true if the source is a valid MLLP message, false otherwise.
     */


    @Override
    public boolean canHandle(Object source) {
        boolean canHandle = false;
         if (source instanceof String hl7String) {
             try {
                PipeParser parser = new PipeParser();
                parser.parse(hl7String);
                canHandle = true;
             } catch (HL7Exception e) {
            canHandle = false;
        }
    }
    LOG.info("MllpHandler:: canHandle called. Source type: {}, Result: {}",
            source != null ? source.getClass().getSimpleName() : "null", canHandle);
    return canHandle;
   }


    /**
     * Processes the MLLP message by delegating to the MessageProcessorService.
     *
     * @param source  The MLLP message to process.
     * @param context The request context containing metadata for processing.
     * @return A map containing the result of the processing.
     */
    @Override
    public Map<String, String> handleAndProcess(Object source, RequestContext context) {
        String interactionId = context != null ? context.getInteractionId() : "unknown";
        LOG.info("MllpHandler:: handleAndProcess called. interactionId={}", interactionId);
        String hl7Payload = (String) source;
        Map<String, String> result = processorService.processMessage(context, hl7Payload);
        LOG.info("MllpHandler:: Processing complete in handleAndProcess. interactionId={}", interactionId);
        return result;
    }
}