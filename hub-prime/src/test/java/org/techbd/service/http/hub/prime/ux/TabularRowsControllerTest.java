package org.techbd.service.http.hub.prime.ux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.techbd.CoreUdiReaderConfig;

import lib.aide.tabular.TabularRowsRequest;

@SpringBootTest
@AutoConfigureMockMvc
class TabularRowsControllerTest {

    @InjectMocks
    private TabularRowsController tabularRowsController;

    @Mock
    private CoreUdiReaderConfig udiReaderConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testInvalidSchemaName_tabularRows() {
        // Prepare invalid input
        String invalidSchemaName = "valid_schema'; DROP TABLE users; --";
        String validTableName = "valid_table";
        TabularRowsRequest payload = new TabularRowsRequest(
                0, 100,
                null, null, null, false, null,
                null, null, null, null, null
        );

        // Assert that an IllegalArgumentException is thrown for an invalid schema name
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRows(invalidSchemaName, validTableName, payload, false, true));
        assertEquals("Invalid schema or table name.", exception.getMessage());
    }

    @Test
    void testInvalidTableName_tabularRows() {
        // Prepare invalid input
        String validSchemaName = "valid_schema";
        String invalidTableName = "valid_table'; DROP TABLE users; --";
        TabularRowsRequest payload = new TabularRowsRequest(
                0, 100,
                null, null, null, false, null,
                null, null, null, null, null
        );

        // Assert that an IllegalArgumentException is thrown for an invalid table name
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRows(validSchemaName, invalidTableName, payload, false, true));
        assertEquals("Invalid schema or table name.", exception.getMessage());
    }

    @Test
    void testInvalidSchemaName_tabularRowsCustom() {
        // Prepare invalid input
        String invalidSchemaName = "valid 'schema'; DROP TABLE users; --";
        String validTableName = "valid_table";
        String validColName = "valid_col_name";
        String validColValue = "valid_col_value";

        TabularRowsRequest payload = new TabularRowsRequest(
                0, 100,
                null, null, null, false, null,
                null, null, null, null, null
        );

        // Assert that an IllegalArgumentException is thrown for an invalid table name
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustom(invalidSchemaName, validTableName, validColName, validColValue));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testInvalidTableName_tabularRowsCustom() {
        // Prepare invalid input
        String validSchemaName = "valid_schema";
        String invalidTableName = "valid_table'; DROP TABLE users; --";
        String validColName = "valid_col_name";
        String validColValue = "valid_col_value";

        TabularRowsRequest payload = new TabularRowsRequest(
                0, 100,
                null, null, null, false, null,
                null, null, null, null, null
        );

        // Assert that an IllegalArgumentException is thrown for an invalid table name
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustom(validSchemaName, invalidTableName, validColName, validColValue));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testInvalidColumnName_tabularRowsCustom() {
        // Prepare invalid input
        String validSchemaName = "valid_schema";
        String validTableName = "valid_table";
        String invalidColName = "'valid_col_name'; DROP TABLE users; --";
        String validColValue = "valid_col_value";

        TabularRowsRequest payload = new TabularRowsRequest(
                0, 100,
                null, null, null, false, null,
                null, null, null, null, null
        );

        // Assert that an IllegalArgumentException is thrown for an invalid table name
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustom(validSchemaName, validTableName, invalidColName, validColValue));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    ////
    @Test
    void testTabularRowsCustomWithMultipleParams_SQLInjectionAttempt_SchemaName() {
        String schemaName = "validSchema'; DROP TABLE users; --"; // SQL injection attempt in schema name
        String tableName = "validTable";
        String columnName = "validColumn";
        String columnValue = "validValue";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParams(schemaName, tableName, columnName, columnValue, columnName2, columnValue2));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParams_SQLInjectionAttempt_TableName() {
        String schemaName = "validSchema";
        String tableName = "validTable'; DROP TABLE users; --"; // SQL injection attempt in table name
        String columnName = "validColumn";
        String columnValue = "validValue";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParams(schemaName, tableName, columnName, columnValue, columnName2, columnValue2));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParams_SQLInjectionAttempt_ColumnName() {
        String schemaName = "validSchema";
        String tableName = "validTable";
        String columnName = "validColumn'; DROP TABLE users; --"; // SQL injection attempt in column name
        String columnValue = "validValue";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParams(schemaName, tableName, columnName, columnValue, columnName2, columnValue2));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParams_SQLInjectionAttempt_ColumnName2() {
        String schemaName = "validSchema";
        String tableName = "validTable";
        String columnName = "validColumn";
        String columnValue = "validValue";
        String columnName2 = "validColumn2'; DROP TABLE users; --"; // SQL injection attempt in column2 name
        String columnValue2 = "validValue2";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParams(schemaName, tableName, columnName, columnValue, columnName2, columnValue2));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    ///
    @Test
    void testTabularRowsCustomWithMultipleParamsChecks_SQLInjectionAttempt_SchemaName() {
        String schemaName = "validSchema'; DROP TABLE users; --"; // SQL injection attempt in schema name
        String tableName = "validTable";
        String columnName1 = "validColumn1";
        String columnValue1 = "validValue1";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";
        String columnName3 = "validColumn3";
        String columnValue3 = "validValue3";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParamsChecks(schemaName, tableName, columnName1, columnValue1, columnName2, columnValue2, columnName3, columnValue3));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParamsChecks_SQLInjectionAttempt_TableName() {
        String schemaName = "validSchema";
        String tableName = "validTable'; DROP TABLE users; --"; // SQL injection attempt in table name
        String columnName1 = "validColumn1";
        String columnValue1 = "validValue1";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";
        String columnName3 = "validColumn3";
        String columnValue3 = "validValue3";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParamsChecks(schemaName, tableName, columnName1, columnValue1, columnName2, columnValue2, columnName3, columnValue3));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParamsChecks_SQLInjectionAttempt_ColumnName1() {
        String schemaName = "validSchema";
        String tableName = "validTable";
        String columnName1 = "validColumn1'; DROP TABLE users; --"; // SQL injection attempt in column 1 name
        String columnValue1 = "validValue1";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";
        String columnName3 = "validColumn3";
        String columnValue3 = "validValue3";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParamsChecks(schemaName, tableName, columnName1, columnValue1, columnName2, columnValue2, columnName3, columnValue3));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParamsChecks_SQLInjectionAttempt_ColumnName2() {
        String schemaName = "validSchema";
        String tableName = "validTable";
        String columnName1 = "validColumn1";
        String columnValue1 = "validValue1";
        String columnName2 = "validColumn2'; DROP TABLE users; --"; // SQL injection attempt in column 2 name
        String columnValue2 = "validValue2";
        String columnName3 = "validColumn3";
        String columnValue3 = "validValue3";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParamsChecks(schemaName, tableName, columnName1, columnValue1, columnName2, columnValue2, columnName3, columnValue3));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }

    @Test
    void testTabularRowsCustomWithMultipleParamsChecks_SQLInjectionAttempt_ColumnName3() {
        String schemaName = "validSchema";
        String tableName = "validTable";
        String columnName1 = "validColumn1";
        String columnValue1 = "validValue1";
        String columnName2 = "validColumn2";
        String columnValue2 = "validValue2";
        String columnName3 = "validColumn3'; DROP TABLE users; --"; // SQL injection attempt in column 3 name
        String columnValue3 = "validValue3";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, ()
                -> tabularRowsController.tabularRowsCustomWithMultipleParamsChecks(schemaName, tableName, columnName1, columnValue1, columnName2, columnValue2, columnName3, columnValue3));
        assertEquals("Invalid schema or table or column name.", exception.getMessage());
    }
}
