package lib.aide.tabular;

public interface TabularRowsSupplier<P> {
    /**
     * Constructs a TabularRowsResponse based on the provided context.
     *
     * @return a TabularRowsResponse containing the result data.
     */
    TabularRowsResponse<P> response();
}
