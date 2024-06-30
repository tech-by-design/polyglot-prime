/**
 * @class AGGridAide
 * @classdesc A helper class for initializing and managing AG Grid instances
 * with customizable options and styles. This class facilitates the creation
 * of AG Grid elements and provides utility functions for rendering cells
 * and formatting data.
 */
export class AGGridAide {
    /**
     * Constructs a new AGGridAide instance.
     * @param {Object} gridOptions - The configuration options for AG Grid.
     * @param {Object} [gridDivStyles={ height: "750px" }] - The CSS styles to be applied to the grid container.
     */
    constructor(gridOptions, gridDivStyles = { height: "750px" }) {
        this.gridOptions = gridOptions;
        this.gridDivStyles = gridDivStyles;
        this.setupDefaultStyles('agGridDefaultStyles');
    }

    /**
     * Sets up the default styles for the AG Grid. If the styles are not already
     * present, this method creates and appends them to the document head.
     * @param {string} styleId - The unique identifier for the style element.
     */
    setupDefaultStyles(styleId) {
        if (!document.getElementById(styleId)) {
            const style = document.createElement('style');
            style.id = styleId;
            style.innerHTML = `
                .ag-theme-alpine .ag-root-wrapper {
                    border: none;
                }
            `;
            document.head.appendChild(style);
        }
    }

    /**
     * Initializes the AG Grid instance within a specified HTML element.
     * @param {string} gridDivId - The ID of the HTML element where the grid will be rendered.
     */
    init(gridDivId) {
        const gridDiv = document.querySelector(`#${gridDivId}`);
        agGrid.createGrid(gridDiv, this.gridOptions);
        Object.assign(gridDiv.style, this.gridDivStyles);
    }

    /**
     * Creates a cell renderer for displaying clickable links that open a modal when clicked.
     * @param {Function} onClick - The function to be called when the link is clicked.
     * @param {ModalAide} modalAide - An instance of ModalAide to manage the modal behavior.
     * @returns {Function} The cell renderer function.
     */
    static modalCellRenderer(onClick, modalAide) {
        return function (params) {
            if (params.value) {
                const link = document.createElement('a');
                link.href = '#';
                link.innerText = params.value;
                link.addEventListener('click', function (e) {
                    e.preventDefault();
                    onClick(params.value, modalAide);
                });
                return link;
            } else {
                return null;
            }
        }
    }

    /**
     * Creates a value formatter for displaying Unix timestamp values as formatted
     * date and time strings.
     * @returns {Function} The value formatter function.
     */
    static dateTimeValueFormatter() {
        return function (params) {
            if (params.value) {
                let milliseconds = params.value * 1000;
                let date = new Date(milliseconds);
                let options = {
                    timeZone: 'America/New_York',
                    year: 'numeric',
                    month: '2-digit',
                    day: '2-digit',
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                    hour12: false
                };
                let formatter = new Intl.DateTimeFormat('en-US', options);
                return formatter.format(date);
            }
            return '';
        }
    }
}

/**
 * @class AGGridAideBuilder
 * @classdesc A builder class for constructing AGGridAide instances with customizable
 * options. This class provides a fluent API for setting various grid options and styles.
 */
export class AGGridAideBuilder {
    /**
     * Constructs a new AGGridAideBuilder instance with default grid options.
     */
    constructor() {
        this.gridOptions = {
            defaultColDef: {
                flex: 1,
                minWidth: 100,
                resizable: true,
                sortable: true,
                filter: true,
                enablePivot: true
            },
            columnDefs: [],
            sideBar: true,
            autoSizeStrategy: { type: "fitCellContents" },
            rowModelType: 'serverSide',
            serverSideDatasource: null,
        };
        this.gridDivStyles = { height: "750px" };
    }

    /**
     * Sets the column definitions for the AG Grid.
     * @param {Array} columnDefs - The column definitions.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withColumnDefs(columnDefs) {
        this.gridOptions.columnDefs = columnDefs;
        return this;
    }

    /**
     * Sets the default column definitions for the AG Grid.
     * @param {Object} defaultColDef - The default column definitions.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withDefaultColDef(defaultColDef) {
        this.gridOptions.defaultColDef = defaultColDef;
        return this;
    }

    /**
     * Sets the sidebar configuration for the AG Grid.
     * @param {Object} sideBar - The sidebar configuration.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withSideBar(sideBar) {
        this.gridOptions.sideBar = sideBar;
        return this;
    }

    /**
     * Sets the server-side datasource for the AG Grid.
     * @param {string} dataSourceUrl - The URL of the server-side datasource.
     * @param {Function} [withSecondaryColumns=null] - A function to update secondary columns if pivot mode is enabled.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withServerSideDatasource(dataSourceUrl, withSecondaryColumns = null) {
        this.gridOptions.serverSideDatasource = {
            getRows: async (params) => {
                const jsonRequest = JSON.stringify(params.request, null, 2);
                try {
                    const response = await fetch(dataSourceUrl, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: jsonRequest
                    });
                    if (response.ok) {
                        const result = await response.json();
                        if (withSecondaryColumns) {
                            try {
                                if (params.request.pivotMode && params.request.pivotCols.length > 0) {
                                    let secondaryColDefs = withSecondaryColumns(result.data, params.request.valueCols);
                                    params.api.updateGridOptions({ columnDefs: secondaryColDefs })
                                } else {
                                    // why is this being done?
                                    // params.api.updateGridOptions({ columnDefs: [] })
                                }
                            } catch (error) {
                                console.error("Error in updateSecondaryColumns:", error);
                            }
                        }
                        params.success({
                            rowData: result.data,
                        });
                    } else {
                        console.error(`[EnterpriseDatasource] Error: ${response.statusText}`);
                        params.fail();
                    }
                } catch (error) {
                    console.error(`[EnterpriseDatasource] Error: ${error.message}`);
                    params.fail();
                }
            }
        };
        return this;
    }

    /**
     * Sets the ModalAide instance to be used for rendering modal popups.
     * @param {ModalAide} modalAide - The ModalAide instance.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withModalAide(modalAide) {
        this.modalAide = modalAide;
        return this;
    }

    /**
     * Sets the styles for the grid container.
     * @param {Object} styles - The styles to be applied to the grid container.
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withGridDivStyles(styles) {
        this.gridDivStyles = { ...this.gridDivStyles, ...styles };
        return this;
    }

    /**
     * Builds and returns an AGGridAide instance with the configured options and styles.
     * @returns {AGGridAide} The configured AGGridAide instance.
     */
    build() {
        return new AGGridAide(this.gridOptions, this.gridDivStyles);
    }
}
