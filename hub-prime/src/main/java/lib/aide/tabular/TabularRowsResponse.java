package lib.aide.tabular;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Nullable;

/**
 * Record representing the server-side response for rows request from AG Grid.
 * This record includes the rows of data requested and an indication of the total number of rows,
 * along with optional provenance information and error reporting.
 *
 * @param provenance         Optional provenance information about the data query.
 * @param data               The list of maps representing the rows returned for the current page.
 * @param lastRow            The index of the last row plus one if there are more rows, or -1 if this is the last row.
 * @param uxReportableError  Optional user-experience reportable error message.
 * @param <P>                The type of provenance information.
 */
public record TabularRowsResponse<P>(
        @Nullable P provenance,
        List<Map<String, Object>> data,
        int lastRow,
        @Nullable String uxReportableError
) {}
