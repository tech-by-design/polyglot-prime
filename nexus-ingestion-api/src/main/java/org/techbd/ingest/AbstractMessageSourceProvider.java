package org.techbd.ingest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.techbd.ingest.commons.Constants;
import org.techbd.ingest.commons.MessageSourceType;
import org.techbd.ingest.config.AppConfig;
import org.techbd.ingest.model.RequestContext;
import org.techbd.ingest.util.HttpUtil;

import jakarta.servlet.http.HttpServletRequest;

public abstract class AbstractMessageSourceProvider implements MessageSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMessageSourceProvider.class.getName());
    @Autowired
    protected AppConfig appConfig;

    /**
     * Function to handle string content ingestion requests.
     *
     * @param content The string content to be ingested.
     * @param headers The request headers containing metadata.
     * @param request The HTTP servlet request.
     * @return A response entity containing the result of the ingestion process.
     * @throws Exception If an error occurs during processing.
     */
    public RequestContext createRequestContext(
            String interactionId,
            Map<String, String> headers,
            HttpServletRequest request,
            long fileSize,
            String originalFileName) {
        LOG.info("DataIngestionController:: Creating RequestContext. interactionId={}", interactionId);
        Instant now = Instant.now();
        if (null == headers) {
            headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                String value = request.getHeader(header);
                headers.put(header, value);
            }
        }
        String timestamp = String.valueOf(now.toEpochMilli());
        ZonedDateTime uploadTime = now.atZone(ZoneOffset.UTC);
        String userAgent = headers.getOrDefault(Constants.REQ_HEADER_USER_AGENT, Constants.DEFAULT_USER_AGENT);
        String fullRequestUrl = request.getRequestURL().toString();
        String queryParams = request.getQueryString();
        String protocol = request.getProtocol();
        String localAddress = request.getLocalAddr();
        String remoteAddress = request.getRemoteAddr();

        LOG.info("DataIngestionController:: RequestContext built for interactionId={}",
                interactionId);

        return new RequestContext(
                headers,
                request.getRequestURI(),
                getTenantId(headers),
                interactionId,
                uploadTime,
                timestamp,
                originalFileName,
                fileSize,
                getDataKey(interactionId, headers, originalFileName),
                getMetaDataKey(interactionId, headers, originalFileName),
                getFullS3DataPath(interactionId, headers, originalFileName),
                userAgent,
                fullRequestUrl,
                queryParams,
                protocol,
                localAddress,
                remoteAddress,
                getSourceIp(headers),
                getDestinationIp(headers),
                getDestinationPort(headers), 
                getAcknowledgementKey(interactionId, headers, originalFileName),
                (!MessageSourceType.HTTP_INGEST.equals(getMessageSource()) && !MessageSourceType.HTTP_HOLD.equals(getMessageSource()) ) ?  
                getFullS3AcknowledgementPath(interactionId, headers, originalFileName) : null,
                getFullS3MetadataPath(interactionId, headers, originalFileName),
                getMessageSource(),getDataBucketName(),getMetadataBucketName());
    }
    @Override
    public String getTenantId(Map<String, String> headers) {
       return HttpUtil.extractTenantId(headers);
    }

    @Override
    public String getSourceIp(Map<String, String> headers) {
        return HttpUtil.extractSourceIp(headers);
    }

    @Override
    public String getDestinationIp(Map<String, String> headers) {
        return HttpUtil.extractDestinationIp(headers);
    }

    @Override
    public String getDestinationPort(Map<String, String> headers) {
        return HttpUtil.extractDestinationPort(headers);        
    }
}
