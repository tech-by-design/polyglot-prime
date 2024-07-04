package lib.aide.sql.dynamic;

public class TextColumnFilter extends ColumnFilter {

    String filter;
    String type;

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
