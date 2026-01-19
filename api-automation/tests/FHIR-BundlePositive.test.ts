import { expect, test } from "@playwright/test";
import fs from "fs";
import path from "path";
import Logger from "../utils/logger-util.ts"
import { runValidBundleTest } from "../sections/request_validate_data.ts";
import dotenv from "dotenv";

const logger = new Logger();
dotenv.config({ path: path.resolve(__dirname, "../.env") });
const hostname = process.env.HOST_NAME;
if (!hostname) {
    throw new Error("Environment variable HOST_NAME is not defined");
}
const fhirbundle = process.env.FHIR_BUNDLE_VALIDATE;
const tenant = process.env.TENANT;
const endpoint = `${hostname}${fhirbundle}`;
console.log(`FHIR Bundle Validate Endpoint: ${endpoint}`);


const errors: string[] = [];

test("POSTC 1: Verify successful submission of assessment bundles which include information used to determine a patient's eligibility, along with supporting information", async ({ request }) => {
    test.setTimeout(50000);
    logger.info(`Starting test: POSTC 1 - Verify successful submission of assessment bundles which include information used to determine a patient's eligibility, along with supporting information`);
    await runValidBundleTest(
        "AHCHRSNQuestionnaireResponse.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 1 - Test execution completed");
});

test("POSTC 2: Verify successful submission of AHC HRSN Screening Response includes all questions from the Accountable Health Communities Health-Related Social Needs Screening Tool", async ({ request }) => {
    logger.info(`Starting test: POSTC 2 - Verify successful submission of AHC HRSN Screening Response includes all questions from the Accountable Health Communities Health-Related Social Needs Screening Tool`);
    await runValidBundleTest(
        "AHCHRSNScreeningResponse.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 2 - Test execution completed");
});

test("POSTC 3: Verify successful submission of a screening bundle in which the patient denies consent", async ({ request }) => {
    logger.info(`Starting test: POSTC 3 - Verify successful submission of a screening bundle in which the patient denies consent`);
    await runValidBundleTest(
        "PatientNegativeConsent.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 3 - Test execution completed");
});

test("POSTC 4: Verify successful submission of a screening bundle that includes the standardized 12 AHC HRSN Screening Tool questions and supporting information", async ({ request }) => {
    logger.info(`Starting test: POSTC 4 - Verify successful submission of a screening bundle that includes the standardized 12 AHC HRSN Screening Tool questions and supporting information`);
    await runValidBundleTest(
        "NYScreeningResponse.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 4 - Test execution completed");
});

test("POSTC 5: Verify successful submission of a screening bundle that includes the 12 AHC HRSN Screening Tool questions, with one of the interpersonal safety questions declined", async ({ request }) => {
    logger.info(`Starting test: POSTC 5 - Verify successful submission of a screening bundle that includes the 12 AHC HRSN Screening Tool questions, with one of the interpersonal safety questions declined`);
    await runValidBundleTest(
        "NYScreeningResponseExampleDeclined9to12.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 5 - Test execution completed");
});

test("POSTC 6: Verify successful submission of a screening bundle that includes the 12 AHC HRSN Screening Tool questions, with one of the interpersonal safety questions unanswered", async ({ request }) => {
    logger.info(`Starting test: POSTC 6 - Verify successful submission of a screening bundle that includes the 12 AHC HRSN Screening Tool questions, with one of the interpersonal safety questions unanswered`);
    await runValidBundleTest(
        "NYScreeningResponseExampleUnknown1to8.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 6 - Test execution completed");
});

test("POSTC 7: Verify successful submission of an assessment bundles that include information used to determine a patient's eligibility, along with supporting information. ", async ({ request }) => {
    logger.info(`Starting test: POSTC 7 - Verify successful submission of an assessment bundles that include information used to determine a patient's eligibility, along with supporting information. `);
    await runValidBundleTest(
        "ObservationAssessmentFoodInsecurity.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 7 - Test execution completed");
});

test("POSTC 8: Verify successful submission of a serviceRequest bundle that documents the initial referral following the screening and assessment", async ({ request }) => {
    logger.info(`Starting test: POSTC 8 - Verify successful submission of a serviceRequest bundle that documents the initial referral following the screening and assessment`);
    await runValidBundleTest(
        "ServiceRequestExample.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 8 - Test execution completed");
});

test("POSTC 9: Verify successful submission of a task bundles that document the response and the status of the response from the organization performing services to fulfill an unmet need", async ({ request }) => {
    logger.info(`Starting test: POSTC 9 - Verify successful submission of Task bundles that document the response and the status of the response from the organization performing services to fulfill an unmet need`);
    await runValidBundleTest(
        "TaskCompletedExample.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 9 - Test execution completed");
});

test("POSTC 10: Verify successful submission of a task bundles with multiple encounters", async ({ request }) => {
    logger.info(`Starting test: POSTC 10 - Verify successful submission of a task bundles with multiple encounters`);
    await runValidBundleTest(
        "TaskExampleMultipleEncounters.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 10 - Test execution completed");
});

test("POSTC 11: Verify successful submission of a task bundle that captures a provision of service to fulfill an unmet need that is still in progress", async ({ request }) => {
    logger.info(`Starting test: POSTC 11 - Verify successful submission of a task bundle that captures a provision of service to fulfill an unmet need that is still in progress`);
    await runValidBundleTest(
        "TaskOutputProcedureExample.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 11 - Test execution completed");
});

test("POSTC 12: Verify successful submission of a bundle that captures information about a healthcare provider", async ({ request }) => {
    logger.info(`Starting test: POSTC 12 - Verify successful submission of a bundle that captures information about a healthcare provider`);
    await runValidBundleTest(
        "Organization-ProviderExample.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 12 - Test execution completed");
});

test("POSTC 13: Verify successful submission of a bundle that captures information about a community-based organization", async ({ request }) => {
    logger.info(`Starting test: POSTC 13 - Verify successful submission of a bundle that captures information about a community-based organization`);
    await runValidBundleTest(
        "Organization-OrganizationExampleCBO.json",
        endpoint as string,
        tenant as string,
        request
    );
    logger.info("POSTC 13 - Test execution completed");
});
