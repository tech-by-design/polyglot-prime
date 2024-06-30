export class ShellAide {
    static SSP_UNKNOWN = "UNKNOWN";
    static SSP_JAVA_SPRING_BOOT = "java-spring-boot";
    #serverSideProfile;
    #javaServletContextPath;

    constructor(options = {}) {
        const { serverSideProfile = this.selectOp('meta[name="shellAideServerSideProfile"]', (meta) => { return meta.content }) } = options;

        switch (serverSideProfile) {
            case ShellAide.SSP_JAVA_SPRING_BOOT:
                this.#setupJavaSpringBootProfile(options);
                break;
            default:
                this.#serverSideProfile = ShellAide.SSP_UNKNOWN;
        }
        if (!this.#serverSideProfile || this.#serverSideProfile == ShellAide.SSP_UNKNOWN)
            console.warn(`No valid options.serverSideProfile or <meta name="shellAideServerSideProfile"> provided, ShellAide may not work properly.`);
    }

    /**
    * Setup all the Shell Aide constructs needed to present Java Spring Boot user agents.
    */
    #setupJavaSpringBootProfile(options) {
        this.#serverSideProfile = ShellAide.SSP_JAVA_SPRING_BOOT;
        const { javaServletContextPath = this.selectOp('meta[name="ssrServletContextPath"]', (meta) => { return meta.content }) } = options;
        this.#javaServletContextPath = javaServletContextPath;

        if (!this.#javaServletContextPath)
            console.warn(`No valid options.javaServletContextPath or <meta name="ssrServletContextPath"> provided, ShellAide may not work properly for '${this.#serverSideProfile}' profile.`);
    }

    get serverSideProfile() {
        return this.serverSideProfile;
    }

    get javaServletContextPath() {
        return this.javaServletContextPath;
    }

    /**
     * Constructs a full URL using the servlet context path and a relative path.
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
            case ShellAide.SSP_JAVA_SPRING_BOOT:
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
}

export default ShellAide;
