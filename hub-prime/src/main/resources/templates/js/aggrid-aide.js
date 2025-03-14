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
    constructor(gridOptions, gridDivStyles = { height: "750px" }, theme = 'legacy') {
        this.gridOptions = gridOptions;
        this.gridOptions.theme = theme; 
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
                    onClick(params, modalAide);
                });
                link.style.textDecoration = 'underline';
                return link;
            } else {
                return null;
            }
        }
    }

    /**
     * Creates a value formatter for displaying date in the MM/DD/YYYY 
     * format.
     * @returns {Function} The value formatter function.
     */
    static dateFormatter() {
        return function (params) {
            if (params.value) {
                let date = new Date(params.value);
                let month = date.getMonth() + 1;
                let formattedDate = month + '/' + date.getDate() + '/' + date.getFullYear();
                return formattedDate;
            }
            return '';
        }
    }

    /**
     * Creates a value formatter for displaying Unix timestamp values as formatted
     * date and time strings.
     * @returns {Function} The value formatter function.
     */
    static dateTimeValueFormatter(inSeconds = true) {
        return function (params) {
            if (params.value) {
                let milliseconds = params.value;
                if (inSeconds) {
                    milliseconds = params.value * 1000;
                }
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

    /**
     * Creates a value formatter for displaying timestamps with timezone
     * information in 'MM/DD/YYYY, HH:MM:SS' format
     * @returns {Function} The value formatter function.
     */
    static isoDateTimeValueFormatter() {
        return function (params) {
            if (params.value) {
                let date = new Date(params.value);
                if (isNaN(date)) {
                    // Handle invalid date
                    return params.value;
                }
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
            sideBar: false, // TODO: turn this back on when Pivots work
            pivotMode: false,
            autoSizeStrategy: { type: "fitCellContents" },
            rowModelType: 'serverSide',
            serverSideDatasource: null,
            tooltipShowDelay: 500,
            masterDetail: false,
            detailCellRendererParams: null,
            detailRowAutoHeight: false
        };
        this.gridDivStyles = { height: "750px" };
    }
    /**
     * Sets the detailRowAutoHeight for the AG Grid.
     * @param {Array} detailRowAutoHeight - The detailRowAutoHeight
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withDetailRowAutoHeight(detailRowAutoHeight) {
        this.gridOptions.detailRowAutoHeight = detailRowAutoHeight;
        return this;
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
 * Sets the master details definitions for the AG Grid.
 * @param {Array} masterDetail - The master detail definitions.
 * @returns {AGGridAideBuilder} The builder instance.
 */
    withMasterDetail(masterDetail) {
        this.gridOptions.masterDetail = masterDetail;
        return this;
    }
    // New method: withGridOptions
    withGridOptions(options) {
        // Merge the provided options into this.gridOptions
        Object.assign(this.gridOptions, options);
        return this;
    }

    /**
      * Sets the default cell renderer definitions for the AG Grid.
      * @param {Array} columnDefs - The Default cell renderer definitions.
      * @returns {AGGridAideBuilder} The builder instance.
      */
    withDetailCellRendererParams(detailCellRendererParams) {
        this.gridOptions.detailCellRendererParams = detailCellRendererParams;
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
     * @param {Object} [inspect={}] - Object to optionally refine how errors are reported and content is to be transformed.
     * @param {Function} [inspect.includeGeneratedSqlInResp] - Optionally set to true to have the server include the SQL it generated in serverRespPayload 
     * @param {Function} [inspect.includeGeneratedSqlInErrorResp] - Optionally set to true to have the server include the SQL it generated in serverRespPayload if there's an error (indepenently of includeGeneratedSqlInResp)
     * @param {Function} [inspect.secondaryColsError] - Called when error encountered in updateSecondaryColumns
     * @param {Function} [inspect.resultServerError] - Called when a UX-reportable error is reported in serverRespPayload so that the UI can be properly updated
     * @param {Function} [inspect.fetchRespNotOK] - Called when fetch completed but the result is not an HTTP 200 (OK)
     * @param {Function} [inspect.fetchError] - Called when unhandle-able error encountered during fetch
     * @param {Function} [inspect.beforeRequest] - Optional method which can preview or modify reqPayload before it is sent to the server
     * @param {Function} [inspect.beforeSuccess] - Optional method which can preview or modify serverRespPayload before AGGrid { rowData: [] } object is built
     * @param {Function} [inspect.customizedContent] - Optional method which can replace the rowData and other content sent to AGGrid in params.success(?)
     * @returns {AGGridAideBuilder} The builder instance.
     */
    withServerSideDatasource(dataSourceUrl, withSecondaryColumns = null, inspect = {}) {
        this.gridOptions.serverSideDatasource = {
            getRows: async (params) => {
                //params.request.valueCols = params.request?.pivotCols && params.request?.pivotCols.length > 0 ? params.request.pivotCols : params.request.valueCols;
                const {
                    includeGeneratedSqlInResp = false, // TODO: set this to false after initial development is concluded
                    includeGeneratedSqlInErrorResp = false,

                    // hooks to customize how errors are reported
                    secondaryColsError = async (dataSourceUrl, reqPayload, serverRespPayload, error, respMetrics) => console.error("[ServerDatasource] Error in updateSecondaryColumns:", { dataSourceUrl, reqPayload, result: serverRespPayload, error, respMetrics }),
                    resultServerError = async (dataSourceUrl, reqPayload, serverRespPayload, respMetrics) => console.warn("[ServerDatasource] Error in server result:", { dataSourceUrl, reqPayload, result: serverRespPayload, respMetrics }),
                    fetchRespNotOK = async (dataSourceUrl, reqPayload, response, respMetrics) => console.error(`[ServerDatasource] Fetched response not OK: ${response.statusText}`, { dataSourceUrl, reqPayload, response, respMetrics }),
                    fetchError = async (dataSourceUrl, reqPayload, error) => console.error(`[ServerDatasource] Fetch error: ${error}`, { dataSourceUrl, reqPayload, error }),

                    // hooks to preview/modify payload before the request or preview/modify results after success
                    beforeRequest = async (reqPayload, dataSourceUrl) => { },
                    beforeSuccess = async (serverRespPayload, respMetrics, reqPayload, dataSourceUrl) => { },

                    // refine the `success` ({ rowData: [] }) object sent to AGGrid to merge or join other data
                    customizedContent = async (success, serverRespPayload, respMetrics, reqPayload, dataSourceUrl) => success,
                } = inspect;

                const reqPayload = {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Include-Generated-SQL-In-Response': includeGeneratedSqlInResp,
                        'X-Include-Generated-SQL-In-Error-Response': includeGeneratedSqlInErrorResp,
                    },
                    // reqPayload is used for inspection so start with body in useful object form
                    body: params.request
                };
                try {
                    await beforeRequest?.(reqPayload, dataSourceUrl);
                    const response = await fetch(dataSourceUrl, {
                        ...reqPayload,
                        // body needs to be string on the way out
                        body: JSON.stringify(reqPayload.body, null, 2)
                    });
                    const respMetrics = response.headers ? {
                        startTime: response.headers.get("X-Observability-Metric-Interaction-Start-Time"),
                        finishTime: response.headers.get("X-Observability-Metric-Interaction-Finish-Time"),
                        durationMillisecs: response.headers.get("X-Observability-Metric-Interaction-Duration-Nanosecs"),
                        durationNanosecs: response.headers.get("X-Observability-Metric-Interaction-Duration-Millisecs"),
                    } : {};
                    if (respMetrics && window.layout?.observability?.metricsCollection) {
                        window.layout.addIdentifiableMetrics(`fetch-${dataSourceUrl}`, respMetrics);
                    }
                    // Check if the URL or content indicates a session timeout
                    console.log('Response URL:' + response.url);
                    console.log('Location Header:' + response.headers.get('Location'));
                    if (response.url.includes('/?timeout=true')) {
                        window.location.href = '/?timeout=true'; // Redirect to login page
                        return; // Stop further processing
                    }
                    // Check if the Location header indicates a session timeout
                    if (response.headers.get('Location')?.includes('/?timeout=true') ||
                        response.headers.get('Location')?.includes('/login')) {
                        window.location.href = '/?timeout=true'; // Redirect to login page
                        return;
                    }
                    if (response.ok) {
                        const serverRespPayload = await response.json();
                        if (withSecondaryColumns) {
                            try {
                                if (params.request.pivotMode && params.request.pivotCols.length > 0) {
                                    let secondaryColDefs = withSecondaryColumns(serverRespPayload.data, params.request.valueCols);
                                    params.api.updateGridOptions({ columnDefs: secondaryColDefs })
                                } else {
                                    // why is this being done?
                                    // params.api.updateGridOptions({ columnDefs: [] })
                                }
                            } catch (error) {
                                await secondaryColsError?.(dataSourceUrl, reqPayload, serverRespPayload, error);
                            }
                        }
                        if (serverRespPayload.uxReportableError) await resultServerError?.(dataSourceUrl, reqPayload, serverRespPayload);
                        await beforeSuccess?.(serverRespPayload, respMetrics, reqPayload, dataSourceUrl);
                        const successArgs = {
                            rowData: serverRespPayload.data,
                        };
                        params.success(customizedContent
                            ? await customizedContent(successArgs, serverRespPayload, respMetrics, reqPayload, dataSourceUrl)
                            : successArgs);
                    } else {
                        await fetchRespNotOK?.(dataSourceUrl, reqPayload, response, respMetrics);
                        params.fail();
                    }
                } catch (error) {
                    await fetchError?.(dataSourceUrl, reqPayload, error);
                    params.fail();
                }
            }
        };
        return this;
    }
    withServerSideDatasourceGET(dataSourceUrl, withSecondaryColumns = null, inspect = {}) {
        this.gridOptions.serverSideDatasource = {
            getRows: async (params) => {
                const {
                    includeGeneratedSqlInResp = true,
                    includeGeneratedSqlInErrorResp = true,

                    secondaryColsError = async (dataSourceUrl, serverRespPayload, error, respMetrics) => console.error("[ServerDatasource] Error in updateSecondaryColumns:", { dataSourceUrl, result: serverRespPayload, error, respMetrics }),
                    resultServerError = async (dataSourceUrl, serverRespPayload, respMetrics) => console.warn("[ServerDatasource] Error in server result:", { dataSourceUrl, result: serverRespPayload, respMetrics }),
                    fetchRespNotOK = async (dataSourceUrl, response, respMetrics) => console.error(`[ServerDatasource] Fetched response not OK: ${response.statusText}`, { dataSourceUrl, response, respMetrics }),
                    fetchError = async (dataSourceUrl, error) => console.error(`[ServerDatasource] Fetch error: ${error}`, { dataSourceUrl, error }),

                    beforeRequest = async (dataSourceUrl) => { },
                    beforeSuccess = async (serverRespPayload, respMetrics, dataSourceUrl) => {
                        console.log('Full Server Response:', serverRespPayload);

                        const data = serverRespPayload;

                        if (!Array.isArray(data)) {
                            console.error('Error: serverRespPayload is not an array.');
                            params.fail();
                            return;
                        }

                        console.log('Extracted data:', data);

                        const valueCols = data.length > 0 ? Object.keys(data[0]).map(key => ({
                            headerName: key.replace(/_/g, ' ').toUpperCase(),
                            field: key
                        })) : [];

                        console.log('Generated valueCols:', valueCols);
                    },

                    customizedContent = async (success, serverRespPayload, respMetrics, dataSourceUrl) => success,
                } = inspect;

                try {
                    await beforeRequest?.(dataSourceUrl);

                    // Directly fetch data from the provided URL without adding parameters
                    const response = await fetch(dataSourceUrl, {
                        method: 'GET',
                        headers: {
                            'X-Include-Generated-SQL-In-Response': includeGeneratedSqlInResp,
                            'X-Include-Generated-SQL-In-Error-Response': includeGeneratedSqlInErrorResp,
                        }
                    });

                    const respMetrics = response.headers ? {
                        startTime: response.headers.get("X-Observability-Metric-Interaction-Start-Time"),
                        finishTime: response.headers.get("X-Observability-Metric-Interaction-Finish-Time"),
                        durationMillisecs: response.headers.get("X-Observability-Metric-Interaction-Duration-Nanosecs"),
                        durationNanosecs: response.headers.get("X-Observability-Metric-Interaction-Duration-Millisecs"),
                    } : {};

                    if (respMetrics && window.layout?.observability?.metricsCollection) {
                        window.layout.addIdentifiableMetrics(`fetch-${dataSourceUrl}`, respMetrics);
                    }

                    if (response.ok) {
                        const serverRespPayload = await response.json();

                        await beforeSuccess?.(serverRespPayload, respMetrics, dataSourceUrl);

                        const data = Array.isArray(serverRespPayload) ? serverRespPayload : [];
                        const valueCols = data.length > 0 ? Object.keys(data[0]).map(key => ({
                            headerName: key.replace(/_/g, ' ').toUpperCase(),
                            field: key
                        })) : [];

                        if (data.length > 0) {
                            params.success({
                                rowData: data,
                                columnDefs: valueCols
                            });
                        } else {
                            params.success({ rowData: [] });
                            params.api.showNoRowsOverlay();
                        }
                    } else {
                        await fetchRespNotOK?.(dataSourceUrl, response, respMetrics);
                        params.fail();
                    }
                } catch (error) {
                    await fetchError?.(dataSourceUrl, error);
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
