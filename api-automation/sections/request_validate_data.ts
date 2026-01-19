import { APIRequestContext, expect } from "@playwright/test";
import Logger from "../utils/logger-util"
import fs from "fs";
import path from "path";
import { test } from "@playwright/test";

const logger = new Logger();
const errors: string[] = [];

export function buildPostConfig(url: string, payload: any, tenant: string) {
  return {
    getUrl: url,
    method: "POST",
    requestBody: payload,
    headers: {
      "Content-Type": "application/json",
      "X-TechBD-Tenant-ID": tenant
    }
  };
}

export async function performRequest(
  apiConfig: {
    getUrl: string;
    method: string;
    requestBody?: any;
    headers: { "Content-Type": string; "X-TechBD-Tenant-ID": string };
  },
  request: APIRequestContext
): Promise<number | undefined | object> {
  let response;

  try {
    response = await request.post(apiConfig.getUrl, {
      headers: apiConfig.headers,
      data: apiConfig.requestBody,
    });
    const statusCode = response.status();

    // Handle HTTP status codes
    switch (statusCode) {
      case 200:
        if (await response.body()) {
          logger.info(`Request succeeded with response code: ${statusCode}`);
          const responseBody = await response.text(); // Get the body content
          if (
            response.headers()["content-type"]?.includes("application/json")
          ) {
            try {
              // console.log(JSON.parse(responseBody));
              return JSON.parse(responseBody); // Parse the body if it's JSON
            } catch (error) {
              logger.error("Failed to parse JSON response", error);
              errors.push("Failed to parse JSON response");
              throw new Error("Failed to parse JSON response");
              // return null;
            }
          } else {
            // Handle plain text response and wrap it in a JSON object
            logger.warn("Response is not JSON. Wrapping it in a JSON object.");
            return { ResponseBody: responseBody };
          }
        }
        break;
      case 400:
        logger.error(`400 Bad Request`);
        errors.push(`400 Bad Request`);
        throw new Error(`400 Bad Request`);
      case 401:
        logger.error("401 Unauthorized: Invalid credentials or headers.");
        errors.push("401 Unauthorized: Invalid credentials or headers.");
        throw new Error("401 Unauthorized: Invalid credentials or headers.");
      case 403:
        logger.error(`403 Forbidden`);
        errors.push(`403 Forbidden`);
        throw new Error(`403 Forbidden`);
      case 404:
        logger.error(`404 Not Found`);
        errors.push(`404 Not Found`);
        throw new Error(`404 Not Found`);
      case 408:
        logger.error(`408 Request Timeout`);
        errors.push(`408 Request Timeout`);
        throw new Error(`408 Request Timeout`);
      case 409:
        logger.error(`409 Conflict`);
        errors.push(`409 Conflict`);
        throw new Error(`409 Conflict`);
      case 429:
        logger.error(`429 Too Many Requests`);
        errors.push(`429 Too Many Requests`);
        throw new Error(`429 Too Many Requests`);
      case 500:
        logger.error(`500 Internal Server Error`);
        errors.push(`500 Internal Server Error`);
        throw new Error(`500 Internal Server Error`);
      case 502:
        logger.error(`502 Bad Gateway`);
        errors.push(`502 Bad Gateway`);
        throw new Error(`502 Bad Gateway`);
      case 503:
        logger.error(`503 Service Unavailable`);
        errors.push(`503 Service Unavailable`);
        throw new Error(`503 Service Unavailable`);
      case 504:
        logger.error(`504 Gateway Timeout`);
        errors.push(`504 Gateway Timeout`);
        throw new Error(`504 Gateway Timeout`);
      default:
        logger.error(`Unexpected status code: ${statusCode}`);
        errors.push(`Unexpected status code: ${statusCode}`);
        throw new Error(`Unexpected status code: ${statusCode}`);
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    logger.error(`Request failed: ${message}`);
    errors.push(`Request failed: ${message}`);
    throw error;
  }

  if (errors.length > 0) {
    logger.error("Errors encountered during configuration:", errors);
  }
}

export interface ExpectedIssue {
  severity?: string;
  messageContains: string;
}

export function validateIssues(interactionId: string | undefined, validationResults: any[], expectedIssues: ExpectedIssue[] = []) {
  const collectedIssues: { diagnostics: string; severity: string }[] = [];
  if (!Array.isArray(expectedIssues)) {
    throw new Error("expectedIssues must be an array");
  }
  // Extract all errors and warnings from validationResults
  for (const result of validationResults) {
    if (result.operationOutcome?.issue) {
      //console.log("operationOutcome issues:", result.operationOutcome.issue);
      for (const issue of result.operationOutcome.issue) {
        const sev = issue.severity?.toLowerCase() || "";
        if (sev === "error" || sev === "warning" || sev === "fatal") {
          collectedIssues.push({
            diagnostics: issue.diagnostics || "",
            severity: sev
          });
        }
      }
    }
  }
  //console.table(collectedIssues);
  expectedIssues.forEach(expected => {
    const found = collectedIssues.some(issue =>
      issue.diagnostics.includes(expected.messageContains) &&
      (expected.severity ? issue.severity === expected.severity : true)
    );
    if (!found) {
      errors.push(`Expected issue NOT found:\n` +
        `severity: ${expected.severity}\n` +
        `contains message: ${expected.messageContains} in interactionId: ${interactionId ?? "unknown"}`);
      if (errors.length > 0) {
        logger.error(`Errors encountered during validation:${errors}`);
      }
      throw new Error(`Expected validation error message not found: ${errors}`);
    } else {
      logger.info(`Expected issue found:\n` +
        `severity: ${expected.severity}\n` +
        `contains message: ${expected.messageContains} in interactionId: ${interactionId ?? "unknown"}`);
    }
  });
}

interface ApiResponse {
  OperationOutcome: {
    device?: {
      deviceId: string;
      deviceName: string;
    };
    statusUrl?: string;
    resourceType: string;
    techBdVersion?: string;
    bundleSessionId?: string;
    valid: boolean;
    validationResults?: Array<any>;
    techByDesignDisposition?: Array<any>;
  };
}

export async function runBundleValidationTest(
  jsonFile: string,
  fhirbundle: string,
  tenant: string,
  request: any,
  expectedIssues: Array<{ severity: string; messageContains: string }>
) {
  let responseData: ApiResponse | null = null;
  let config: any;

  // STEP 1 : Prepare for Bundle endpoint request
  await test.step("Prepare for Bundle endpoint request", async () => {
    const filePath = path.join(__dirname, "../testdata/FHIR-Data/NegativeTestData", jsonFile);
    const jsonData = fs.readFileSync(filePath, "utf-8");
    const fhirPayload = JSON.parse(jsonData);
    config = buildPostConfig(fhirbundle, fhirPayload, tenant);
  });

  // STEP 2 : Perform POST request to Bundle endpoint
  await test.step("Perform POST request to Bundle endpoint", async () => {
    logger.info("Sending POST request to the Bundle endpoint");
    responseData = await performRequest(config, request) as ApiResponse;
    //console.log("Response Data:", responseData);
    if (!responseData) {
      throw new Error("Response data is null or undefined.");
    } else {
      logger.info("Bundle post request done successfully.");
    }
  });

  // STEP 3 : Validate error message in validation results
  await test.step("Validate error messages", async () => {
    if (responseData) {
      const interactionId = responseData.OperationOutcome.bundleSessionId;
      const validationResults = responseData.OperationOutcome.validationResults;
      validateIssues(interactionId, validationResults ?? [], expectedIssues);
    }
  });

}

export async function runValidBundleTest(
  jsonFile: string,
  fhirbundle: string,
  tenant: string,
  request: any

) {
  let responseData: ApiResponse | null = null;
  let config: any;

  // STEP 1 : Prepare for Bundle endpoint request
  await test.step("Prepare for Bundle endpoint request", async () => {
    const filePath = path.join(__dirname, "../testdata/FHIR-Data/PositiveTestData", jsonFile);
    const jsonData = fs.readFileSync(filePath, "utf-8");
    const fhirPayload = JSON.parse(jsonData);
    config = buildPostConfig(fhirbundle, fhirPayload, tenant);
  });

  // STEP 2 : Perform POST request to Bundle endpoint
  await test.step("Perform POST request to Bundle endpoint", async () => {
    logger.info("Sending POST request to the Bundle endpoint");
    responseData = await performRequest(config, request) as ApiResponse;
    //console.log("Response Data:", responseData);
    if (!responseData) {
      throw new Error("Response data is null or undefined.");
    } else {
      logger.info("Bundle post request done successfully.");
    }
  });

  // STEP 3 : Validate error message in validation results
  await test.step("Validate response code", async () => {
    if (responseData) {
      const interactionId = responseData.OperationOutcome.bundleSessionId;
      const validationResults = responseData.OperationOutcome.validationResults;
      if (!validationResults || validationResults.length === 0) {
        throw new Error("validationResults is undefined or empty.");
      }
      const validfhir = validationResults[0].valid;
      expect(validfhir).toBe(true);
      logger.info(`Bundle with interactionId: ${interactionId} validated successfully with valid status: ${validfhir}`);
    }
  });

}                                                           