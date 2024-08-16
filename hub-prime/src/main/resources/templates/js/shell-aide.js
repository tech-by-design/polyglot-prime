/**
 * Class representing a single cookie.
 */
export class Cookie {
    #name;
    #value;

    /**
     * Create a cookie instance.
     * @param {string} name - The name of the cookie.
     * @param {string} value - The value of the cookie.
     */
    constructor(name, value) {
        this.#name = name;
        this.#value = value;
    }

    get name() { return this.#name; }
    get value() { return this.#value; }

    json(defaultValue = null, onError = null) {
        try {
            return this.#value?.trim().length
                ? JSON.parse(this.#value)
                : defaultValue;
        } catch {
            return onError
                ? (typeof onError === "function" ? onError(this, this.#value) : onError)
                : defaultValue;
        }
    }
}

/**
 * Class representing a collection of cookies.
 */
export class Cookies {
    static #SINGLETON = null;
    #cookies = {};

    /**
     * Private constructor to enforce singleton pattern.
     */
    constructor(cookieString = document.cookie) {
        const cookies = cookieString.split('; ');
        for (let cookie of cookies) {
            const [encodedName, encodedValue] = cookie.split('=');
            const cookieName = decodeURIComponent(encodedName);
            if (cookieName.trim().length) {
                this.#cookies[cookieName] = new Cookie(
                    cookieName, 
                    // on the Java side URLEncoder adds + for spaces but decodeURIComponent does not handle '+'
                    decodeURIComponent(encodedValue.replace(/\+/g, ' '))
                );
            }
        }
    }

    /**
     * Get the singleton instance of Cookies for typed access to browser's default document.cookie.
     * @returns {Cookies} The singleton instance of Cookies.
     */
    static get SINGLETON() {
        if (!Cookies.#SINGLETON) {
            Cookies.#SINGLETON = new Cookies();
        }
        return Cookies.#SINGLETON;
    }

    get all() {
        return this.#cookies;
    }

    /**
     * Get a cookie by name and return an instance of the Cookie class.
     * @param {string} name - The name of the cookie to retrieve.
     * @returns {Cookie|null} An instance of Cookie if found, otherwise null.
     */
    get(name) {
        return this.#cookies[name];
    }
}

export class UxReportableMetrics {
    #identity;
    #metrics = {};

    constructor(identity, metrics = {}) {
        this.#identity = identity;
        this.#metrics = metrics;
    }

    get identity() { return this.#identity; }
    get metrics() { return this.#metrics; }
}

export class UxReportableMetricsCollection {
    #reportableMetrics = [];

    constructor() {
    }

    get reportableMetrics() {
        return this.#reportableMetrics;
    }

    addReportable(uxReportableMetrics) {
        this.#reportableMetrics.push(uxReportableMetrics);
    }

    getReportable(identity) {
        this.#reportableMetrics.find(rm => rm.identity == identity);
    }
}

export class ObservabilityAide {
    #serverRespMetricsCookie;
    #uxrMetricsCollection = new UxReportableMetricsCollection();
    #pageInitMetrics;

    constructor() {
        // since HTML pages and JavaScript don't have access to HTTP headers, the server
        // should send us interaction response metrics via a cookie so let's grab it
        this.#serverRespMetricsCookie = Cookies.SINGLETON.get("Observability-Metric-Interaction-Active");
        this.#pageInitMetrics = this.#serverRespMetricsCookie ? new UxReportableMetrics("page-init", this.#serverRespMetricsCookie.json()) : null;
        if (this.#pageInitMetrics) this.#uxrMetricsCollection.addReportable(this.#pageInitMetrics);
    }

    get metricsCollection() { return this.#uxrMetricsCollection; }
    get pageInitMetrics() { return this.#pageInitMetrics; }
}

/**
 * @class ShellAide
 * @classdesc This class provides reusable application shell functionalities 
 * across all pages. It is not specific to any particular functionality but
 * offers generic support methods and configurations useful for the entire
 * application. The "Shell" represents the web browser application shell and
 * acts as the primary collaboration point with the server side.
 * 
 * The concept of `serverSideProfile` allows the same ShellAide to be used
 * across different server-side platforms such as NodeJS, Java, PHP, etc.
 * By acknowledging the server-side profile during construction, ShellAide
 * can adapt its behavior and configurations to match the specifics of the
 * underlying server platform.
 * 
 * - `SSP_UNKNOWN`: Represents an unknown or unsupported server-side profile.
 * - `SSP_JAVA_SPRING_BOOT`: Represents a Java Spring Boot server-side profile, 
 *                           allowing ShellAide to configure settings specific
 *                           to Java environments.
 */
export class ShellAide {
    static GLOBAL_PROPERTY_NAME = "shell";
    static SINGLETON = (windowPropertyName = ShellAide.GLOBAL_PROPERTY_NAME) => window[windowPropertyName];
    static SSP_UNKNOWN = "UNKNOWN";
    static SSP_JAVA_SPRING_BOOT3 = "java-spring-boot-3";

    #serverSideProfile;
    #javaServletContextPath;
    #windowPropertyName;

    /**
     * Constructs a new ShellAide instance.
     * @param {Object} [layoutOptions={}] - Configuration options.
     * @param {string} [layoutOptions.serverSideProfile] - Server side profile, defaults to meta tag content if not provided.
     */
    constructor(options = {}) {
        const { serverSideProfile = this.selectOp('meta[name="shellAideServerSideProfile"]', (meta) => { return meta.content }) } = options;

        switch (serverSideProfile) {
            case ShellAide.SSP_JAVA_SPRING_BOOT3:
                this.#setupJavaSpringBootProfile(options);
                break;
            default:
                this.#serverSideProfile = ShellAide.SSP_UNKNOWN;
        }
        if (!this.#serverSideProfile || this.#serverSideProfile == ShellAide.SSP_UNKNOWN)
            console.warn(`No valid options.serverSideProfile or <meta name="shellAideServerSideProfile"> provided, ShellAide may not work properly.`);
    }

    get windowPropertyName() { return this.#windowPropertyName; }

    /**
     * Register this shell in the window so it's available globally
     * @param {string} [windowPropertyName] - The name of the window property to set the shell instance to
     */
    global(windowPropertyName = ShellAide.GLOBAL_PROPERTY_NAME) {
        this.#windowPropertyName = windowPropertyName;
        window[windowPropertyName] = this;
        return this;
    }

    /**
     * Setup all the Shell Aide constructs needed to present Java Spring Boot user agents.
     * This method is Java specific.
     * @param {Object} options - Configuration options.
     */
    #setupJavaSpringBootProfile(options) {
        this.#serverSideProfile = ShellAide.SSP_JAVA_SPRING_BOOT3;
        const { javaServletContextPath = this.selectOp('meta[name="ssrServletContextPath"]', (meta) => { return meta.content }) } = options;
        this.#javaServletContextPath = javaServletContextPath;

        if (!this.#javaServletContextPath)
            console.warn(`No valid options.javaServletContextPath or <meta name="ssrServletContextPath"> provided, ShellAide may not work properly for '${this.#serverSideProfile}' profile.`);
    }

    /**
     * Gets the server side profile.
     * @returns {string} The server side profile.
     */
    get serverSideProfile() {
        return this.#serverSideProfile;
    }

    /**
     * Gets the Java servlet context path.
     * This method is Java specific.
     * @returns {string} The Java servlet context path.
     */
    get javaServletContextPath() {
        return this.#javaServletContextPath;
    }

    /**
     * Constructs a full URL using the servlet context path and a relative path.
     * This method is Java specific.
     * 
     * @param {string} relativePath - The relative path to append to the context path.
     * @returns {string} The full URL.
     */
    javaServletContextUrl(relativePath) {
        if (this.#javaServletContextPath == null) return relativePath;
        return this.#javaServletContextPath == "/" ? relativePath : (this.#javaServletContextPath + relativePath);
    }

    /**
     * Constructs a full URL using the application server path and a relative path.
     * This method can be used safely across all server-side profiles.
     * 
     * @param {string} relativePath - The relative path to append to the context path.
     * @returns {string} The full URL.
     */
    serverSideUrl(relativePath) {
        switch (this.#serverSideProfile) {
            case ShellAide.SSP_JAVA_SPRING_BOOT3:
                return this.javaServletContextUrl(relativePath);
            default:
                console.warn(`[Shell Aide] Cannot handle serverSideUrl('${relativePath}') for profile '${this.#serverSideProfile}'`);
                return relativePath;
        }
    }

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
    selectOp(...args) {
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

    integrateSandboxConsole() {
        // Our convention is to pass a JS object as a <meta> to inform whether the sandbox console should be configured
        const sandboxConsoleConf = this.selectOp('meta[name="sandboxConsoleConf"]', (meta) => { return meta?.content ? JSON.parse(meta.content) : null });
        if (sandboxConsoleConf == null) return this;

        console.log(`Sandbox console configuration`, sandboxConsoleConf);

        // handle automatic editing of active template, and other developer-focused functionality
        document.addEventListener('DOMContentLoaded', () => {
            let consoleAppended = false;

            const showSandboxConsole = (id = "layout-sandbox-console") => {
                if (consoleAppended) return;
                const consoleDiv = document.createElement('div');
                consoleDiv.innerHTML = `
                    <div id="${id}" class="w-full mt-4 mx-auto bg-gray-100 p-8 rounded-lg shadow-lg">
                        <div id="sandbox-console" style="display: none">
                            <span id="sandbox-console-openFileInEditor-container" style="display: none">
                                <a id="sandbox-console-openFileInEditor-anchor" href="dynamic">dynamic</a>
                            </span> 
                            <a href="/experiment/home.html" style="padding-left: 10px">🧪 Experiments/Debug</a>
                            <json-viewer id="sandbox-console-watch-json"></json-viewer>
                        </div>
                    </div>
                `;
                document.body.appendChild(consoleDiv);
                consoleAppended = true;
            }

            // If there is an edit URL in the sandboxConsoleConf, update the anchor element
            const editUrl = sandboxConsoleConf?.template?.editUrl;
            if (editUrl) {
                showSandboxConsole();
                this.selectOp('#sandbox-console-openFileInEditor-anchor', (a) => {
                    a.innerText = `📄 edit ${sandboxConsoleConf?.template?.canonical ?? "canonical not set"}`;
                    a.href = editUrl;
                }, '#sandbox-console-openFileInEditor-container', (elem) => { elem.style.display = '' });
            }

            // If user agent sandbox console is enabled, update the corresponding elements
            if (sandboxConsoleConf?.enabled) {
                const watch = document.sandboxConsoleWatch ? document.sandboxConsoleWatch : {};
                showSandboxConsole();
                this.selectOp(
                    '#sandbox-console-watch-json', (elem) => { elem.data = { contextPath: this.#javaServletContextPath, sandboxConsoleConf, ...watch } },
                    '#sandbox-console', (elem) => { elem.style.display = '' });
            }
        });

        return this;
    }
}

export class Link {
    #href;
    #text;
    #options;

    /**
     * Constructs a new Link instance
     * @param {Object} [layoutOptions={}] - Configuration options for the route.
     * @param {string} [layoutOptions.href] - the hyperlink reference (URI) for the route
     * @param {string} [layoutOptions.text] - the label for the route (such as anchor text)
     */
    constructor(options = {}) {
        this.#options = options;
        this.#href = options.href;
        this.#text = options.text;
    }

    get href() { return this.#href; }
    get text() { return this.#text; }
    get options() { return this.#options; }
}

export class Links {
    #identity;
    #links;

    /**
     * Constructs a new Links collection instance 
     * @param {Link[]} [links] - the list of Links
     */
    constructor(identity, ...links) {
        this.#identity = identity;
        this.#links = links;
    }

    get identity() { return this.#identity; }
    get links() { return this.#links; }
}

/**
 * @class LayoutAide
 * @classdesc This class provides reusable Layout functionality across all 
 * pages that have titles (headers), primary (level-0) navigation and 
 * breadcrumbs. LayoutAide is meant to be subclassed for customizations.
 */
export class LayoutAide {
    static GLOBAL_PROPERTY_NAME = "layout";
    static SINGLETON = (windowPropertyName = LayoutAide.GLOBAL_PROPERTY_NAME) => window[windowPropertyName];

    #observability = new ObservabilityAide();
    #shellAide;
    #layoutOptions;
    #activeRoute;
    #activeRouteOptions;
    #windowPropertyName;
    #activeRouteURI;
    #activeRouteIsHomePage;
    #breadcrumbs;

    /**
     * Constructs a new LayoutAide instance for a Shell.
     * @param {ShellAide} - The Shell which is bound to the Layout
     * @param {Object} [layoutOptions={}] - Configuration options.
     * @param {boolean} [layoutOptions.logLevel] - Log level
     */
    constructor(shellAide, layoutOptions = { logLevel: "none" }) {
        if (shellAide == null) {
            // if we were not given a ShellAide instance, construct one, set it
            // as the global and use it
            shellAide = new ShellAide()
                .global()
                .integrateSandboxConsole();
        }

        this.#shellAide = shellAide;
        this.#layoutOptions = layoutOptions;
        this.hideNeedsAttentionLink();
    }

    get shellAide() { return this.#shellAide; }
    get observability() { return this.#observability }
    get layoutOptions() { return this.#layoutOptions; }
    get activeRoute() { return this.#activeRoute; }
    get activeRouteURI() { return this.#activeRouteURI; }
    get activeRouteIsHomePage() { return this.#activeRouteIsHomePage; }
    get activeRouteOptions() { return this.#activeRouteOptions; }
    get windowPropertyName() { return this.#windowPropertyName; }
    get breadcrumbs() { return this.#breadcrumbs; }

    /**
     * Hides the "Needs Attention" link on the page by setting its display property to 'none'.
     *
     * This function adds an event listener to the 'DOMContentLoaded' event, which waits for the
     * page to finish loading before executing the code inside the event listener. It then selects
     * the first 'a' element with a 'href' attribute value of '/needs-attention' and sets its
     * display property to 'none' if it exists.
     *
     * @return {void} This function does not return anything.
     */
    hideNeedsAttentionLink() {
        document.addEventListener("DOMContentLoaded", () => {
            const link = document.querySelector('a[href="/needs-attention"]');
            if (link) {
                link.style.display = 'none';
            }
        });
    }

    debugLog(...data) {
        if (this.layoutOptions.logLevel == "debug") {
            console.log(...data);
        }
    }

    /**
     * Register this layout in the window so it's available globally
     * @param {string} [windowPropertyName] - The name of the window property to set the layout instance to
     */
    global(windowPropertyName = LayoutAide.GLOBAL_PROPERTY_NAME) {
        this.#windowPropertyName = windowPropertyName;
        window[windowPropertyName] = this;
        this.debugLog({ propertyName: windowPropertyName, layout: this });
        return this;
    }

    addIdentifiableMetrics(identity, metrics) {
        this.observability.metricsCollection.addReportable(new UxReportableMetrics(identity, metrics));
        this.activeRouteObservability(); // update the UI
    }

    activeRouteObservability() {
        const obsElem = document.getElementById("nav-prime-notification");
        if (obsElem && this.observability.metricsCollection.reportableMetrics.length) {
            let title = "";
            for(const rm of this.observability.metricsCollection.reportableMetrics) {
                const duration = rm.metrics.durationMillisecs > 10 ? `${rm.metrics.durationMillisecs} ms` : `${rm.metrics.durationNanosecs} nanosecs`;
                if(title.length) title += "\n";
                title += `[${rm.identity}] generated: ${rm.metrics.finishTime}\n[${rm.identity}] duration: ${duration}`;
            }
            obsElem.title = title;
        }
    }

    /**
     * Define the current path as its known to the server side
     * @param {string} activeRouteURI the "route" URI (might be the same as window.location.pathname
     *                     or may not be, depending on the server side definition of "context")
     * @param {boolean?} isHomePage whether or not the active route is the home page
     * @param {Object} [options={}] - Configuration options.
     */
    activeRouteURI(activeRouteURI, isHomePage) {
        this.debugLog({ activeRouteURI, isHomePage });
        this.#activeRouteURI = activeRouteURI;
        this.#activeRouteIsHomePage = isHomePage ?? false;

        if (this.#activeRouteURI) {
            // Get the parent element containing all the menu items and distinguish
            // the active page based on what our current location is
            const currentPath = window.location.pathname;
            document.querySelectorAll("#nav-prime a").forEach(item => {
                const href = item.getAttribute("href");
                if (href === currentPath || (currentPath.startsWith(href) && href !== "/")) {
                    item.classList.add("bg-gray-900", "text-white");
                    item.classList.remove("text-gray-300", "hover:bg-gray-700", "hover:text-white");
                }
            });
        }
        return this;
    }

    /**
     * Define the current page title as it will be assigned to document.title, 
     * the page header and breadcrumbs terminal node. 
     * @param {string} title the "route" title
     * @param {Object} [options={}] - Configuration options.
     */
    title(title) {
        this.debugLog({ title });
        document.title = title;
        const headingElem = document.getElementById("heading-prime");
        if (headingElem) headingElem.innerText = document.title;
        return this;
    }

    /**
     * Define the current page breadcrumbs. A `<nav>#nav-breadcrumbs` element 
     * contains breadcrumb templates for the home, inner, and terminal items.
     * This structure is hidden by default and displayed by JavaScript after
     * page load. The `class` and any internal tags (like chevrons, etc.) are
     * not relevant since the breadcrumbs JavaScript will just do structural
     * copies and replacement of `href` and `innerText`. This allows the
     * HTML-defined template (such as Thymeleaf) to be natural and preview-
     * friendly in any browser.
     * 
     * @param {{ href: string; text: string; }[]} breadcrumbs "route" breadcrumbs links collection, null to hide all breadcrumbs
     * @param {Object} [options={}] - Configuration options.
     */
    breadcrumbs(breadcrumbs, isHome) {
        this.debugLog({ breadcrumbs });
        breadcrumbs = breadcrumbs
            ? new Links("breadcrumbs", ...breadcrumbs.map(bc => new Link(bc)))
            : null;
        this.#breadcrumbs = breadcrumbs;
        const breadcrumbNav = document.getElementById("nav-breadcrumbs");
        if (breadcrumbNav == null) {
            // shouldn't call this before body is rendered
            return this;
        }

        if (breadcrumbs == null) {
            breadcrumbNav.classList.add("hidden");
            return this;
        }

        // make a copy of the inner node as a template that we can copy for each breadcrumb link.
        const breadcrumbInnerTemplate = document.getElementById("breadcrumb-inner-template");
        if (breadcrumbInnerTemplate) {
            breadcrumbInnerTemplate.classList.add("hidden"); // in case it's not already hidden.
            const breadcrumbTerminal = document.getElementById("breadcrumb-terminal");
            const existingInner = document.querySelectorAll(".breadcrumb-inner");
            for (const inner of existingInner) {
                if (breadcrumbInnerTemplate !== inner)
                    inner.parentElement.removeChild(inner);
            }
            if (breadcrumbs.links.length) {
                const breadcrumbList = breadcrumbNav.querySelector("ol");
                breadcrumbs.links.forEach(link => {
                    this.debugLog({ link, breadcrumbs });
                    // duplicate the inner node template and just replace links/text
                    const listItem = breadcrumbInnerTemplate.cloneNode(true);
                    const anchor = listItem.querySelector("a");
                    listItem.id = "";
                    if (link.href) anchor.href = link.href;
                    anchor.textContent = link.text;
                    listItem.classList.remove("hidden");
                    breadcrumbList.insertBefore(listItem, breadcrumbTerminal);
                });
                breadcrumbTerminal.querySelector("span").textContent = document.title;
                breadcrumbNav.classList.remove("hidden");
            } else if (!isHome) {
                breadcrumbTerminal.querySelector("span").textContent = document.title;
                breadcrumbNav.classList.remove("hidden");
            } else {
                // the home page needs a little distance from the header since there are no breadcrumbs
                document.querySelector("main > div:first-child").classList.replace("py-2", "py-6");
            }
        } else {
            console.warn(`#breadcrumb-inner-template not found in DOM, unable to fill in breadcrumbs`);
        }

        return this;
    }

    /**
     * Use everything in activeRoute and related content to configure title,
     * navigation, breadcrumbs, etc.
     */
    initActiveRoute() {
        // alawys use this specific order since the later methods depend on the previous ones
        this.activeRouteObservability();
        this.activeRouteURI(this.activeRoute.uri, this.activeRoute.isHomePage);
        this.title(this.activeRoute.title);
        this.breadcrumbs(this.activeRoute.breadcrumbs, this.activeRoute.isHomePage);
        return this;
    }

    /**
     * Set the active route for this layout -- usually this is setup in the <head>
     * and then after the <body> is loaded, call window.layout.initActiveRoute();
     * @param {Object} [activeRoute] - The active route
     * @param {boolean?} [activeRoute.isHomePage] - Whether or not the active route is the app's home page
     * @param {string} [activeRoute.title] - The active route's title
     * @param {string} [activeRoute.uri] - The active route's URI (for lookups, searches, etc.)
     * @param {Link[]} [activeRoute.breadcrumbs] - The active route's beadcrumbs (without the `home` or `terminal` routes)
     * @param {Object} [options={}] - Configuration options.
     */
    setActiveRoute(activeRoute, options = {}) {
        this.#activeRoute = activeRoute;
        this.#activeRouteOptions = options;
        return this;
    }
}

/**
 * @class TwoLevelHorizontalLayoutAide
 * @classdesc This class provides reusable Layout functionality across all 
 * pages that use a "main menu" as the primary navigation, then a "tabs" menu
 * for the secondary level and then a sidebar-based model for the third and
 * subsequent levels of navigation. Every page except the home page also has
 * breadcrumbs navigation.
 */
export class TwoLevelHorizontalLayoutAide extends LayoutAide {
    #tabs;

    get tabs() { return this.#tabs; }

    /**
     * Use everything in activeRoute and related content to configure title,
     * navigation, breadcrumbs, etc.
     */
    initActiveRoute() {
        super.initActiveRoute();
        // this.tabs(activeRoute, options);
        return this;
    }

    /**
     * Set the active route for this layout -- usually this is setup in the <head>
     * and then after the <body> is loaded, call window.layout.initActiveRoute();
     * @param {Object} [activeRoute] - The active route
     * @param {boolean?} [activeRoute.isHomePage] - Whether or not the active route is the app's home page
     * @param {string} [activeRoute.title] - The active route's title
     * @param {string} [activeRoute.uri] - The active route's URI (for lookups, searches, etc.)
     * @param {Links} [activeRoute.breadcrumbs] - The active route's beadcrumbs (without the `home` or `terminal` routes)
     * @param {Links} [activeRoute.tabs] - The active route's siblings which comprise different "tabs"
     * @param {Object} [options={}] - Configuration options.
     */
    setActiveRoute(activeRoute, options = {}) {
        // we added a new item "activeRoute.tabs"
        super.setActiveRoute(activeRoute, options);
        return this;
    }
}
