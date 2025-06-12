package org.techbd.service.csv;

class CsvServiceTest {
    // @Mock
    // private CsvOrchestrationEngine engine;
    // @Mock
    // private UdiPrimeJpaConfig udiPrimeJpaConfig;
    // @Mock
    // private CsvBundleProcessorService csvBundleProcessorService;
    // @Mock
    // private OrchestrationSessionBuilder sessionBuilder;
    // @InjectMocks
    // private CsvService csvService;

    // private HttpServletRequest mockRequest;
    // private HttpServletResponse mockResponse;
    // private MultipartFile testFile;
    // private static final String TEST_TENANT_ID = "testTenant";
    // private static final String TEST_ORIGIN = "testOrigin";
    // private static final String TEST_SFTP_SESSION_ID = "testSessionId";
    
    // @BeforeEach
    // void setUp() {
    //     MockitoAnnotations.openMocks(this);
    //     mockRequest = mock(HttpServletRequest.class);
    //     mockResponse = mock(HttpServletResponse.class);
    //     testFile = new MockMultipartFile("test.csv", "test.csv", "text/csv", "test data".getBytes());
        
    //     when(engine.session()).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withMasterInteractionId(anyString())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withSessionId(anyString())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withTenantId(anyString())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withFile(any())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withRequestParameters(any())).thenReturn(sessionBuilder);
    // }

    // @Nested
    // class ValidateCsvFileTests {
    //       @Test
    // public void shouldReturnsValidateCsvFile_ValidationResults() throws Exception {
    //     // Arrange
    //     MultipartFile file = new MockMultipartFile("test.csv", "test.csv", "text/csv", "test data".getBytes());
    //     HttpServletRequest request = mock(HttpServletRequest.class);
    //     HttpServletResponse response = mock(HttpServletResponse.class);
    //     String tenantId = "testTenant";
    //     String origin = "testOrigin";
    //     String sftpSessionId = "testSessionId";

    //     // Mock InteractionsFilter and its return value
    //     InteractionsFilter interactionsFilter = mock(InteractionsFilter.class);
    //     Interactions.RequestEncountered mockRequestEncountered = mock(Interactions.RequestEncountered.class);
    //     when(interactionsFilter.getActiveRequestEnc(request)).thenReturn(mockRequestEncountered);
    //     // Mock requestId to return a UUID
    //     UUID mockRequestId = UUID.randomUUID();
    //     when(mockRequestEncountered.requestId()).thenReturn(mockRequestId);

    //     DSLContext dslContext = mock(DSLContext.class);
    //     when(udiPrimeJpaConfig.dsl()).thenReturn(dslContext);

    //     CsvOrchestrationEngine.OrchestrationSession session = mock(CsvOrchestrationEngine.OrchestrationSession.class);

    //     when(engine.session()).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withMasterInteractionId(anyString())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withSessionId(anyString())).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withTenantId(tenantId)).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withFile(file)).thenReturn(sessionBuilder);
    //     when(sessionBuilder.withRequestParameters(requestParameters)).thenReturn(sessionBuilder);
    //     when(sessionBuilder.build()).thenReturn(session);

    //     Map<String, Object> expectedValidationResults = new HashMap<>();
    //     expectedValidationResults.put("result", "Test Result");
    //     when(session.getValidationResults()).thenReturn(expectedValidationResults);

    //     // Act
    //     Object result = csvService.validateCsvFile(file, request, response, tenantId, origin, sftpSessionId);

    //     // Assert
    //     assertNotNull(result);
    //     assertEquals(expectedValidationResults, result);
    //     verify(engine).orchestrate(session);
    // }

    //     @Test
    //     void shouldHandleOrchestrationException() {
    //         // Arrange
    //         CsvOrchestrationEngine.OrchestrationSession mockSession = mock(CsvOrchestrationEngine.OrchestrationSession.class);
    //         when(sessionBuilder.build()).thenReturn(mockSession);

    //         // Act & Assert
    //         assertThrows(RuntimeException.class, () -> 
    //             csvService.validateCsvFile(
    //                 testFile, mockRequest, mockResponse,
    //                 TEST_TENANT_ID, TEST_ORIGIN, TEST_SFTP_SESSION_ID
    //             )
    //         );
    //     }

    //     @Nested
    //     class InputValidationTests {
    //         @Test
    //         void shouldRequireNonNullFile() {
    //             assertThrows(NullPointerException.class, () ->
    //                 csvService.validateCsvFile(
    //                     null, mockRequest, mockResponse,
    //                     TEST_TENANT_ID, TEST_ORIGIN, TEST_SFTP_SESSION_ID
    //                 )
    //             );
    //         }

    //         @Test
    //         void shouldRequireNonNullTenantId() {
    //             assertThrows(NullPointerException.class, () ->
    //                 csvService.validateCsvFile(
    //                     testFile, mockRequest, mockResponse,
    //                     null, TEST_ORIGIN, TEST_SFTP_SESSION_ID
    //                 )
    //             );
    //         }
            
    //         @Test
    //         void shouldValidateAllRequiredParameters() {
    //             // Testing all null parameter combinations in one test
    //             assertAll(
    //                 () -> assertThrows(NullPointerException.class, () ->
    //                     csvService.validateCsvFile(null, null, null, null, null, null)),
    //                 () -> assertThrows(NullPointerException.class, () ->
    //                     csvService.validateCsvFile(testFile, mockRequest, mockResponse, null, TEST_ORIGIN, TEST_SFTP_SESSION_ID)),
    //                 () -> assertThrows(NullPointerException.class, () ->
    //                     csvService.validateCsvFile(testFile, null, mockResponse, TEST_TENANT_ID, TEST_ORIGIN, TEST_SFTP_SESSION_ID))
    //             );
    //         }
    //     }
    // }

    // private InteractionsFilter setupInteractionsFilter() {
    //     InteractionsFilter interactionsFilter = mock(InteractionsFilter.class);
    //     Interactions.RequestEncountered mockRequestEncountered = mock(Interactions.RequestEncountered.class);
    //     when(interactionsFilter.getActiveRequestEnc(mockRequest)).thenReturn(mockRequestEncountered);
    //     when(mockRequestEncountered.requestId()).thenReturn(UUID.randomUUID());
    //     return interactionsFilter;
    // }
}



