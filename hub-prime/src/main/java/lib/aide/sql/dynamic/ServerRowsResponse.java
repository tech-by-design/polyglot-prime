package lib.aide.sql.dynamic;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Sets;

public class ServerRowsResponse {
    private List<Map<String, Object>> data;
    private int lastRow;
    private List<String> secondaryColumnFields;

    public ServerRowsResponse() {
    }

    public ServerRowsResponse(final List<Map<String, Object>> data, final int lastRow,
            final List<String> secondaryColumnFields) {
        this.data = data;
        this.lastRow = lastRow;
        this.secondaryColumnFields = secondaryColumnFields;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public int getLastRow() {
        return lastRow;
    }

    public void setLastRow(int lastRow) {
        this.lastRow = lastRow;
    }

    public List<String> getSecondaryColumnFields() {
        return secondaryColumnFields;
    }

    public void setSecondaryColumns(List<String> secondaryColumnFields) {
        this.secondaryColumnFields = secondaryColumnFields;
    }

    public static ServerRowsResponse createResponse(
            final ServerRowsRequest request,
            final List<Map<String, Object>> rows,
            final Map<String, List<String>> pivotValues) {

        final var currentLastRow = request.getStartRow() + rows.size();
        final var lastRow = currentLastRow <= request.getEndRow() ? currentLastRow : -1;

        final var valueColumns = request.getValueCols();

        return new ServerRowsResponse(rows, lastRow,
                pivotValues != null ? getSecondaryColumns(pivotValues, valueColumns) : null);
    }

    private static List<String> getSecondaryColumns(final Map<String, List<String>> pivotValues,
            final List<ColumnVO> valueColumns) {

        // create pairs of pivot col and pivot value i.e. (DEALTYPE,Financial),
        // (BIDTYPE,Sell)...
        final var pivotPairs = pivotValues.entrySet().stream()
                .map(e -> e.getValue().stream()
                        .map(pivotValue -> Pair.of(e.getKey(), pivotValue))
                        .collect(toCollection(LinkedHashSet::new)))
                .collect(toList());

        // create cartesian product of pivot and value columns i.e.
        // Financial_Sell_CURRENTVALUE, Physical_Buy_CURRENTVALUE...
        return Sets.cartesianProduct(pivotPairs)
                .stream()
                .flatMap(pairs -> {
                    // collect pivot cols, i.e. Financial_Sell
                    String pivotCol = pairs.stream()
                            .map(Pair::getRight)
                            .collect(joining("_"));

                    // append value cols, i.e. Financial_Sell_CURRENTVALUE,
                    // Financial_Sell_PREVIOUSVALUE
                    return valueColumns.stream()
                            .map(valueCol -> pivotCol + "_" + valueCol.getField());
                })
                .collect(toList());
    }
}