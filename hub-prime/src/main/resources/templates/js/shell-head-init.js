/**
 * shell-head-init.js provides common "Application Shell" (wrapper) functions used in all layouts.
 * Always call shell-head-init.js as early in the <head> as possible.
 *   <script id="shell-head-init" th:src="@{/presentation/shell/js/shell-head-init.js}"></script>
 */

/**
 * Select DOM elements and apply mutations using selector/callback pairs.
 * The function will loop through the arguments and apply the callback to the element
 * returned by the selector if it exists. Use this instead of document.querySelector
 * directly to reduce chances of calling null results.
 * 
 * @param {...any} args - Variable length arguments in selector/callback pairs.
 * @returns {null|any|any[]} Either null (no results), a scalar, or an array from the results of the callbacks.
 * 
 * Example usage:
 * 
 * const result = layoutSelectOp('selector1', callback1, 'selector2', callback2);
 * if (result) {
 *     console.log(result);
 * }
 */
const layoutSelectOp = (...args) => {
    let results = [];
    // Loop through the arguments two at a time (selector, callback pairs)
    for (let i = 0; i < args.length; i += 2) {
        const element = document.querySelector(args[i]);
        if (element) {
            results.push(args[i + 1](element));
        } else {
            console.warn(`Selector ${i} '${args[i]}' did not find an element, no mutations applied.`);
        }
    }
    return results.length > 1 ? results : (results.length == 1 ? results[0] : null);
};

// Our convention is to pass the servlet context path as <meta name="ssrServletContextPath" th:content="@{/}">
// so that it's available in the web browser. If dynamically building URLs, this value works in concert
// with ssrServletUrl().
const contextPath = layoutSelectOp('meta[name="ssrServletContextPath"]', (meta) => { return meta.content });
if (contextPath == null) console.warn(`<meta name="ssrServletContextPath"> not provided, ssrServletUrl() might not work properly.`);

/**
 * Constructs a full URL using the servlet context path and a relative path.
 * 
 * @param {string} relativePath - The relative path to append to the context path.
 * @returns {string} The full URL.
 */
function ssrServletUrl(relativePath) {
    if (contextPath == null) return relativePath;
    return contextPath == "/" ? relativePath : (contextPath + relativePath);
}

// Retrieve and parse the SSR baggage JSON if available
const ssrBaggageJSON = document.querySelector('meta[name="ssrBaggageJSON"]')?.content?.trim();
if (ssrBaggageJSON.length) {
    document.ssrBaggageJSON = ssrBaggageJSON;
    document.ssrBaggage = JSON.parse(ssrBaggageJSON);
}

// handle SSR baggage, automatic editing of active template, and other developer-focused functionality
document.addEventListener('DOMContentLoaded', function () {
    let consoleAppended = false;

    const showDevlConsole = (id = "layout-devl-console") => {
        if(consoleAppended) return;
        const consoleDiv = document.createElement('div');
        consoleDiv.innerHTML = `
            <div id="${id}" class="w-full mt-4 mx-auto bg-gray-100 p-8 rounded-lg shadow-lg">
                <div id="devl_exposure_container" style="display: none">
                    <span id="devl_openFileInEditor_container" style="display: none">
                        <a id="devl_openFileInEditor_anchor" href="dynamic">dynamic</a>
                    </span> 
                    <a href="/experiment/home.html" style="padding-left: 10px">🧪 Experiments/Debug</a>
                    <json-viewer id="devl_ssrBaggage_userAgentBaggageExposureEnabledJsonViewer"></json-viewer>
                    <p>
                        The above is visible because <code>document.ssrBaggageJSON</code> was
                        sent via <code>&ltmeta name="ssrBaggageJSON" content="\${document.ssrBaggageJSON}"&gt;</code>
                        from the server. You should never share sensitive contents or secrets through SSR Baggage.                        
                    </p>
                </div>
            </div>
        `;
        document.body.appendChild(consoleDiv);
        consoleAppended = true;
    }

    // If there is an edit URL in the SSR baggage, update the anchor element
    const editUrl = document.ssrBaggage?.template?.editUrl;
    if (editUrl) {
        showDevlConsole();
        layoutSelectOp('#devl_openFileInEditor_anchor', (a) => {
            a.innerText = `📄 edit ${document.ssrBaggage?.template?.canonical ?? "canonical not set"}`;
            a.href = editUrl;
        }, '#devl_openFileInEditor_container', (elem) => { elem.style.display = '' });
    }

    // If user agent baggage exposure is enabled, update the corresponding elements
    if (document.ssrBaggage?.userAgentBaggageExposureEnabled) {
        showDevlConsole();
        layoutSelectOp(
            '#devl_ssrBaggage_userAgentBaggageExposureEnabledJsonViewer', (elem) => { elem.data = document.ssrBaggage },
            '#devl_exposure_container', (elem) => { elem.style.display = '' });
    }
});
