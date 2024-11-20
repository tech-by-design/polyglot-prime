package org.techbd.service;


import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.CodeSystem;

import com.fasterxml.jackson.databind.ObjectMapper;
/**
 * Service for looking up CodeSystem entries from JSON files.
 * <p>
 * This service reads a given JSON file containing CodeSystem data, then filters the list of CodeSystems
 * to find one that matches a specific code.
 * </p>
 */
public class CodeSystemLookupService {
private static final Logger LOG = LoggerFactory.getLogger(CodeSystemLookupService.class.getName());
      /**
     * Looks up a CodeSystem entry by its code from a specified JSON file.
     * <p>
     * This method reads the JSON file from the specified path and attempts to find the first CodeSystem
     * entry that matches the given code. The file should contain a list of CodeSystem objects. If the code
     * is found, it returns the matching CodeSystem wrapped in an {@link Optional}. If not found, it returns
     * an empty {@link Optional}.
     * </p>
     *
     * @param jsonFileName the name of the JSON file containing the CodeSystem data
     * @param codeToLookup the code to search for within the CodeSystem entries
     * @return an {@link Optional} containing the matching CodeSystem, or an empty Optional if not found
     */
    public Optional<CodeSystem> lookupCodeInFile(String jsonFileName, String codeToLookup) {
        //TODO - implement caching
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            File file = new File("src/main/resources/shinny/shinny-artifacts/" + jsonFileName);
            List<CodeSystem> codeSystems = objectMapper.readValue(file, objectMapper.getTypeFactory().constructCollectionType(List.class, CodeSystem.class));
            return codeSystems.stream()
                    .filter(codeSystem -> codeSystem.getCode().equals(codeToLookup))
                    .findFirst();
        } catch (IOException e) {
            LOG.error("Exception while fetching code : {} from file :{} "+ codeToLookup,jsonFileName);
            return Optional.empty();
        }
    }
}