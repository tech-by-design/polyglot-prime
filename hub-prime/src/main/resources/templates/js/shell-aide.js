/**
 * @class ShellAide
 * @classdesc This class provides reusable application shell functionalities 
 * across all pages. It is not specific to any particular functionality but
 * offers generic support methods and configurations useful for the entire
 * application.
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
    static SSP_UNKNOWN = "UNKNOWN";
    static SSP_JAVA_SPRING_BOOT = "java-spring-boot";
    #serverSideProfile;
    #javaServletContextPath;

    /**
     * Constructs a new ShellAide instance.
     * @param {Object} [options={}] - Configuration options.
     * @param {string} [options.serverSideProfile] - Server side profile, defaults to meta tag content if not provided.
     */
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
     * This method is Java specific.
     * @param {Object} options - Configuration options.
     */
    #setupJavaSpringBootProfile(options) {
        this.#serverSideProfile = ShellAide.SSP_JAVA_SPRING_BOOT;
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
