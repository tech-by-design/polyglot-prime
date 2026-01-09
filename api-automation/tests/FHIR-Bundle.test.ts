import { expect, test } from "@playwright/test";
import fs from "fs";
import path from "path";
import Logger from "../utils/logger-util"
import { runBundleValidationTest } from "../sections/request_validate_data";
import { expectedValidationIssues } from "../testdata/expectedValidationIssues.ts";
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

test("TC 1: Verify validation error for bundle with an encounter reference but no encounter resource included", async ({ request }) => {
    test.setTimeout(50000);
    logger.info(`Starting test: TC 1 - Verify validation error for bundle with an encounter reference but no encounter resource included`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase1.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase1
    );
    logger.info("TC 1 - Test execution completed");
});

test("TC 2: Verify validation error for bundle with a meta.profile that does not include “shinny.org/us/ny/hrsn” in the URL.", async ({ request }) => {
    logger.info(`Starting test: TC 2 - Verify validation error for bundle with a meta.profile that does not include “shinny.org/us/ny/hrsn” in the URL.`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase2.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase2
    );
    logger.info("TC 2 - Test execution completed");
});

test("TC 3: Verify validation error for bundle with a Screening Observation but no Consent resource", async ({ request }) => {
    logger.info(`Starting test: TC 1 - Verify validation error for bundle with a Screening Observation but no Consent resource`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase3.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase3
    );
    logger.info("TC 3 - Test execution completed");
});

test("TC 4: Verify validation error for bundle with more than one patient", async ({ request }) => {
    logger.info(`Starting test: TC 4 - Verify validation error for bundle with more than one patient`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase4.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase4
    );
    logger.info("TC 4 - Test execution completed");
});

test("TC 5: Verify validation error for bundle without an Encounter", async ({ request }) => {
    logger.info(`Starting test: TC 5 - Verify validation error for bundle without an Encounter`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase5.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase5
    );
    logger.info("TC 5 - Test execution completed");
});

test("TC 6: Verify validation error for bundle without an Organization", async ({ request }) => {
    logger.info(`Starting test: TC 1 - Verify validation error for bundle without an Organization`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase6.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase6
    );
    logger.info("TC 6 - Test execution completed");
});

test("TC 7: Verify validation error for a screening observation without a performer", async ({ request }) => {
    logger.info(`Starting test: TC 1 - Verify validation error for a screening observation without a performer`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase7.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase7
    );
    logger.info("TC 7 - Test execution completed");
});

test("TC 8: Verify validation error for a screening Observation with a performer that does not reference an Organization", async ({ request }) => {
    logger.info(`Starting test: TC 8 - Verify validation error for a screening Observation with a performer that does not reference an Organization`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase8.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase8
    );
    logger.info("TC 8 - Test execution completed");
});

test("TC 9: Verify validation error for a screening without a SDOH code present for the category attribute", async ({ request }) => {
    logger.info(`Starting test: TC 9 - Verify validation error for bundle with an encounter reference but no encounter resource included`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase9.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase9
    );
    logger.info("TC 9 - Test execution completed");
});

test("TC 10: Verify validation error for a screening without a reference to an encounter", async ({ request }) => {
    logger.info(`Starting test: TC 10 - Verify validation error for a screening without a reference to an encounter`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase10.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase10

    );
    logger.info("TC 10 - Test execution completed");
});

test("TC 11: Verify validation error for a screening observation that does not include observation.subject", async ({ request }) => {
    logger.info(`Starting test: TC 11 - Verify validation error for a screening observation that does not include observation.subject`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase11.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase11
    );
    logger.info("TC 11 - Test execution completed");
});

test("TC 12: Verify validation error for an organization without one of the following codes: NPI, TAX, or MA", async ({ request }) => {
    logger.info(`Starting test: TC 12 - Verify validation error for an organization without one of the following codes: NPI, TAX, or MA`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase12.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase12
    );
    logger.info("TC 12 - Test execution completed");
});

test("TC 13: Verify validation error for a patient with an invalid birthdate", async ({ request }) => {
    logger.info(`Starting test: TC 13 - Verify validation error for a patient with an invalid birthdate`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase13.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase13
    );
    logger.info("TC 13 - Test execution completed");
});

test("TC 14: Verify validation error for a patient without a gender provided", async ({ request }) => {
    logger.info(`Starting test: TC 14 - Verify validation error for a patient without a gender provided`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase14.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase14
    );
    logger.info("TC 14 - Test execution completed");
});

test("TC 15: Verify validation error for a patient with multiple CINs", async ({ request }) => {
    logger.info(`Starting test: TC 15 - Verify validation error for a patient with multiple CINs`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase15.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase15
    );
    logger.info("TC 15 - Test execution completed");
});

test("TC 16: Verify validation error for a patient with an invalid CIN", async ({ request }) => {
    logger.info(`Starting test: TC 16 - Verify validation error for a patient with an invalid CIN`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase16.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase16
    );
    logger.info("TC 16 - Test execution completed");
});

test("TC 17: Verify validation error for a patient with multiple MRNs", async ({ request }) => {
    logger.info(`Starting test: TC 17 - Verify validation error for a patient with multiple MRNs`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase17.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase17
    );
    logger.info("TC 17 - Test execution completed");
});

test("TC 18: Verify validation error for bundle with an Encounter that references Location but Location is not included in the bundle", async ({ request }) => {
    logger.info(`Starting test: TC 18 - Verify validation error for bundle with an Encounter that references Location but Location is not included in the bundle`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase18.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase18
    );
    logger.info("TC 18 - Test execution completed");
});

test("TC 19: Verify validation error for Bundle with a Screening Observation that includes a derivedFrom reference but the reference is not included in the bundle", async ({ request }) => {
    logger.info(`Starting test: TC 19 - Verify validation error for Bundle with a Screening Observation that includes a derivedFrom reference but the reference is not included in the bundle`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase19.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase19
    );
    logger.info("TC 19 - Test execution completed");
});

test("TC 20: Verify validation error for Bundle with a Screening Observation that includes a hasMember reference but the reference is not included in the bundle", async ({ request }) => {
    logger.info(`Starting test: TC 20 - Verify validation error for Bundle with a Screening Observation that includes a hasMember reference but the reference is not included in the bundle`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase20.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase20
    );
    logger.info("TC 20 - Test execution completed");
});

test("TC 21: Verify validation error for bundle with a Screening Observation that references a Patient but a Patient resource with a different ID than the reference is included", async ({ request }) => {
    logger.info(`Starting test: TC 21 - Verify validation error for bundle with a Screening Observation that references a Patient but a Patient resource with a different ID than the reference is included`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase21.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase21
    );
    logger.info("TC 21 - Test execution completed");
});

test("TC 22: Verify validation error for a Screening Observation with housing adequacy questions (96778-6) that are not formatted as components", async ({ request }) => {
    logger.info(`Starting test: TC 22 - Verify validation error for a Screening Observation with housing adequacy questions (96778-6) that are not formatted as components`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase22.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase22
    );
    logger.info("TC 22 - Test execution completed");
});

test("TC 23: Verify validation error for a Location that includes an address with a blank row for the second address line", async ({ request }) => {
    logger.info(`Starting test: TC 23 - Verify validation error for a Location that includes an address with a blank row for the second address line`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase23.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase23

    );
    logger.info("TC 23 - Test execution completed");
});

test("TC 24: Verify validation error for an Assessment Observation without a performer", async ({ request }) => {
    logger.info(`Starting test: TC 24 - Verify validation error for an Assessment Observation without a performer`);
    await runBundleValidationTest(
        "ObservationAssessmentFoodInsecurity_testcase24.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase24
    );
    logger.info("TC 24 - Test execution completed");
});

test("TC 25: Verify validation error for an Assessment Observation with a performer that does not reference an Organization", async ({ request }) => {
    logger.info(`Starting test: TC 25 - Verify validation error for an Assessment Observation with a performer that does not reference an Organization`);
    await runBundleValidationTest(
        "ObservationAssessmentFoodInsecurity_testcase25.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase25
    );
    logger.info("TC 25 - Test execution completed");
});

test("TC 26: Verify validation error for an Assessment Observation that does not include an encounter", async ({ request }) => {
    logger.info(`Starting test: TC 26 - Verify validation error for an Assessment Observation that does not include an encounter`);
    await runBundleValidationTest(
        "ObservationAssessmentFoodInsecurity_testcase26.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase26
    );
    logger.info("TC 26 - Test execution completed");
});

test("TC 27: Verify validation error for an Assessment Observation that does not include observation.subject", async ({ request }) => {
    logger.info(`Starting test: TC 27 - Verify validation error for an Assessment Observation that does not include observation.subject`);
    await runBundleValidationTest(
        "ObservationAssessmentFoodInsecurity_testcase27.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase27
    );
    logger.info("TC 27 - Test execution completed");
});

test("TC 28: Verify validation error for a Service Request without a SDOH code present for the category attribute", async ({ request }) => {
    logger.info(`Starting test: TC 28 - Verify validation error for a Service Request without a SDOH code present for the category attribute`);
    await runBundleValidationTest(
        "ServiceRequest_testcase28.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase28
    );
    logger.info("TC 28 - Test execution completed");
});

test("TC 29: Verify validation error for a Task that does not have status reason for one of the following statuses: rejected, cancelled, completed, or failed", async ({ request }) => {
    logger.info(`Starting test: TC 29 - Verify validation error for a Task that does not have status reason for one of the following statuses: rejected, cancelled, completed, or failed`);
    await runBundleValidationTest(
        "Task_testcase29.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase29
    );
    logger.info("TC 29 - Test execution completed");
});

test("TC 30: Verify validation error for a screening Observation with both dataAbsentReason and an observation answer value present.", async ({ request }) => {
    logger.info(`Starting test: TC 30 - Verify validation error for a screening Observation with both dataAbsentReason and an observation answer value present`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase30.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase30
    );
    logger.info("TC 30 - Test execution completed");
});

test("TC 31: Verify mandatory fields validation errors for missing elements in the Bundle resource.", async ({ request }) => {
    logger.info(`Starting test: TC 31 - Verify mandatory fields validation errors for missing elements in the Bundle resource.`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase31.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase31
    );
    logger.info("TC 31 - Test execution completed");
});

test("TC 32: Verify mandatory fields validation errors for missing elements in the Patient resource. ", async ({ request }) => {
    logger.info(`Starting test: TC 32 - Verify mandatory fields validation errors for missing elements in the Patient resource`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase32.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase32
    );
    logger.info("TC 32 - Test execution completed");
});

test("TC 33: Verify mandatory fields validation errors for missing elements in the Organization resource ", async ({ request }) => {
    logger.info(`Starting test: TC 33 - Verify mandatory fields validation errors for missing elements in the Organization resource `);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase33.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase33
    );
    logger.info("TC 33 - Test execution completed");
});

test("TC 34: Verify mandatory fields validation errors for missing elements in the Consent resource ", async ({ request }) => {
    logger.info(`Starting test: TC 34 - Verify mandatory fields validation errors for missing elements in the Consent resource `);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase34.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase34
    );
    logger.info("TC 34 - Test execution completed");
});

test("TC 35: Verify mandatory fields validation errors for missing elements in the Encounter resource ", async ({ request }) => {
    logger.info(`Starting test: TC 35 - Verify mandatory fields validation errors for missing elements in the Encounter resource `);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase35.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase35
    );
    logger.info("TC 35 - Test execution completed");
});

test("TC 36: Verify mandatory fields validation errors for missing elements in the Observation resource ", async ({ request }) => {
    logger.info(`Starting test: TC 36 - Verify mandatory fields validation errors for missing elements in the Observation resource `);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase36.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase36
    );
    logger.info("TC 36 - Test execution completed");
});

test("TC 37: Verify validation error for an array object not declared as an array in the Bundle.", async ({ request }) => {
    logger.info(`Starting test: TC 37 - Verify validation error for an array object not declared as an array in the Bundle.`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase37.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase37
    );
    logger.info("TC 37 - Test execution completed");
});

test("TC 38: Verify validation error for a non-array object incorrectly declared as an array in the Bundle", async ({ request }) => {
    logger.info(`Starting test: TC 38 - Verify validation error for a non-array object incorrectly declared as an array in the Bundle`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase38.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase38
    );
    logger.info("TC 38 - Test execution completed");
});

test("TC 39: Verify validation error for a screening resource containing unrecognized property elements", async ({ request }) => {
    logger.info(`Starting test: TC 39 - Verify validation error for a screening resource containing unrecognized property elements`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase39.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase39
    );
    logger.info("TC 39 - Test execution completed");
});

test("TC 40: Verify validation error for a screening bundle with invalid Bundle last updated date", async ({ request }) => {
    logger.info(`Starting test: TC 40 - Verify validation error for a screening bundle with invalid Bundle last updated date`);
    await runBundleValidationTest(
        "AHCHRSNScreeningResponse_testcase40.json",
        endpoint as string,
        tenant as string,
        request,
        expectedValidationIssues.testcase40
    );
    logger.info("TC 40 - Test execution completed");
});


