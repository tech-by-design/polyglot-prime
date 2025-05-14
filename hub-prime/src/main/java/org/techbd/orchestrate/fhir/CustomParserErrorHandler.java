package org.techbd.orchestrate.fhir;

//import static org.techbd.orchestrate.fhir.LenientErrorHandler.ourLog;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ScalarType;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ValueType;

public class CustomParserErrorHandler extends LenientErrorHandler {

    private final List<OperationOutcomeIssueComponent> parserIssues = new ArrayList<>();

    public CustomParserErrorHandler() {

    }

    public List<OperationOutcomeIssueComponent> getParserIssues() {
        return parserIssues;
    }

    private void addIssue(OperationOutcome.IssueSeverity severity, OperationOutcome.IssueType type, String message) {
        OperationOutcomeIssueComponent issue = new OperationOutcomeIssueComponent();
        issue.setSeverity(severity);
        issue.setCode(type);
        issue.setDiagnostics(message);
        parserIssues.add(issue);
    }

    @Override
    public void unknownElement(IParseLocation theLocation, String theElementName) {
        addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.INVALID,
                "Unknown element '" + theElementName + "' found while parsing");
    }

    @Override
    public void incorrectJsonType(
            IParseLocation theLocation,
            String theElementName,
            ValueType theExpected,
            ScalarType theExpectedScalarType,
            ValueType theFound,
            ScalarType theFoundScalarType) {

        String message = createIncorrectJsonTypeMessage(
                theElementName, theExpected, theExpectedScalarType, theFound, theFoundScalarType);

        // Add issue to OperationOutcome or similar structure (from your base class)
        addIssue(OperationOutcome.IssueSeverity.ERROR, OperationOutcome.IssueType.STRUCTURE, message);
    }

}
