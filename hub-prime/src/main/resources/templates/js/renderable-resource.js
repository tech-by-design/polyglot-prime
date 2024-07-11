import { unified } from 'https://cdn.jsdelivr.net/npm/unified@11.0.5/+esm';
import remarkParse from 'https://cdn.jsdelivr.net/npm/remark-parse@11.0.0/+esm';
import remarkFrontmatter from 'https://cdn.jsdelivr.net/npm/remark-frontmatter@5.0.0/+esm';
import remarkParseFrontmatter from 'https://cdn.jsdelivr.net/npm/remark-parse-frontmatter@1.0.3/+esm';
import rehypeUrlInspector from 'https://cdn.jsdelivr.net/npm/rehype-url-inspector@2.0.2/+esm';
import remarkRehype from 'https://cdn.jsdelivr.net/npm/remark-rehype@11.1.0/+esm';
import remarkGfm from 'https://cdn.jsdelivr.net/npm/remark-gfm@4.0.0/+esm';
import rehypeStringify from 'https://cdn.jsdelivr.net/npm/rehype-stringify@10.0.0/+esm';

/**
 * Resource represents a server-side arbitrary content or "resource" that should
 * be user-agent (client-side browser) rendered.
 */
export class RenderableResource {
    static RESOURCE_RENDERED_EVENT_NAME = `resourceRendered`;

    #resourceURI;
    #fetchedURL;
    #contentType;
    #title;
    #breadcrumbs;
    #renderElemSupplier;
    #options;

    constructor(resourceURI, fetchedURL, response, renderElemSupplier, options = {}) {
        this.#resourceURI = resourceURI;
        this.#fetchedURL = fetchedURL;
        this.#renderElemSupplier = renderElemSupplier;
        this.#contentType = response.headers.get('Content-Type');
        this.#title = response.headers.get('Resource-Title');
        this.#breadcrumbs = response.headers.get('Resource-Breadcrumbs');
        this.#options = options;
    }

    get resourceURI() { return this.#resourceURI; }
    get fetchedURL() { return this.#fetchedURL; }
    get contentType() { return this.#contentType; }
    get title() { return this.#title; }
    get breadcrumbs() { return this.#breadcrumbs; }
    get renderElemSupplier() { return this.#renderElemSupplier; }
    get options() { return this.#options; }

    async render(response) {
        const targetElem = this.renderElemSupplier();
        if (!targetElem) return this;
        targetElem.innerHTML = `Unable to render '${this.contentType}' resource type in abstract method Resource.render (${this.fetchedURL}).`;
        console.warn(`Resource.render should not be called, it means a proper subclass was not instantiated`, {
            method: `Resource.render`,
            content: (await response.text()),
            inspect: { this: this, targetElem }
        });
        this.dispatchRenderedEvent();
        return this;
    }

    dispatchRenderedEvent() {
        document.dispatchEvent(new CustomEvent(RenderableResource.RESOURCE_RENDERED_EVENT_NAME, {
            detail: {
                resource: this,
                resourceURI: this.resourceURI,
                fetchedURL: this.fetchedURL,
                contentType: this.contentType,
            }
        }));
        return this;
    }

    static from(resourceURI, fetchedURL, response, renderElemSupplier, resourceConstructOptions) {
        switch (response.headers.get('Content-Type')) {
            case "text/html":
                return new HtmlResource(resourceURI, fetchedURL, response, renderElemSupplier, resourceConstructOptions);
            case "text/markdown":
                return new MarkdownResource(resourceURI, fetchedURL, response, renderElemSupplier, resourceConstructOptions);
            case "text/mdx":
                return new UnsupportedResource(resourceURI, fetchedURL, response, renderElemSupplier, resourceConstructOptions);
            default:
                return new RenderableResource(resourceURI, fetchedURL, response, renderElemSupplier, resourceConstructOptions);
        }
    }
}

export class UnsupportedResource extends RenderableResource {
    async render(response) {
        const targetElem = this.renderElemSupplier();
        if (!targetElem) return this;
        targetElem.innerHTML = `'${this.contentType}' is not supported yet.`;
        console.warn(`Need to implement rendering for ${this.contentType} content type.`, {
            method: `UnsupportedResource.render`,
            content: (await response.text()),
            inspect: { this: this, targetElem }
        });
        this.dispatchRenderedEvent();
        return this;
    }
}

export class HtmlResource extends RenderableResource {
    async render(response) {
        const targetElem = this.renderElemSupplier();
        if (!targetElem) return this;
        targetElem.innerHTML = (await response.text());
        this.dispatchRenderedEvent();
        return this;
    }
}

export class MarkdownResource extends RenderableResource {
    async render(response) {
        const targetElem = this.renderElemSupplier();
        if (!targetElem) return this;

        const markdown = await response.text();
        const transformed = await unified()
            .use(remarkParse, { fragment: true })
            .use(remarkFrontmatter)
            .use(remarkParseFrontmatter)
            .use(remarkGfm) // support GFM (autolink literals, footnotes, strikethrough, tables, tasklists)
            .use(remarkRehype, { allowDangerousHtml: true })
            .use(rehypeUrlInspector, {
                inspectEach: (node) => {
                    this.options.markdownUrlInspector?.(node, this);
                }
              })
            .use(rehypeStringify, { allowDangerousHtml: true })
            .process(markdown);
        targetElem.innerHTML = transformed.toString();
        this.dispatchRenderedEvent();
        return this;
    }
}

export class RenderableResources {
    #serverSideUrlSupplier;
    #renderElemSupplier;
    #resourceConstructOptions;

    constructor(serverSideUrlSupplier, renderElemSupplier, resourceConstructOptions = {}) {
        this.#serverSideUrlSupplier = serverSideUrlSupplier;
        this.#renderElemSupplier = renderElemSupplier;
        this.#resourceConstructOptions = resourceConstructOptions;
    }

    get serverSideUrlSupplier() { return this.#serverSideUrlSupplier };
    get renderElemSupplier() { return this.#renderElemSupplier };
    get resourceConstructOptions() { return this.#resourceConstructOptions };

    async fromURI(resourceUrl) {
        let fetchURL, response, resource;
        try {
            fetchURL = this.serverSideUrlSupplier(resourceUrl);
            response = await fetch(fetchURL);
            resource = RenderableResource.from(
                resourceUrl, fetchURL, response,
                () => this.renderTargetElem(),
                this.resourceConstructOptions);
            await resource.render(response);
        } catch (error) {
            console.error(`Error fetching resource`, {
                method: `Resources.fromURI`,
                resourceUrl,
                error,
                this: this,
                fetchURL, response, resource,
            });

            const targetElem = this.renderTargetElem();
            if (targetElem) {
                targetElem.innerHTML = `Error loading resource, inspect errors in console.`;
            }
        }
    }

    /**
     * Execute renderElemSupplier as a function and return the value with
     * appropriate console warnings and debug inspectors.
     * @returns non-NULL if renderElemSupplier results is an HTML element
     */
    renderTargetElem() {
        const targetElem = this.renderElemSupplier();
        if (!targetElem) {
            console.warn(`Unable to find target element for Resource`, {
                method: `Resources.renderTargetElem`,
                inspect: { this: this, renderElemSupplier: this.renderElemSupplier }
            });
            return null;
        }

        if (targetElem instanceof HTMLElement && ('innerHTML' in targetElem && 'innerText' in targetElem)) {
            return targetElem;
        }

        console.warn(`Target element for Resource is not an HTML element`, {
            method: `Resources.renderTargetElem`,
            targetElem,
            inspect: { this: this }
        });
        return null;
    }
}

/**
 * Sets up an event listener to handle resource load errors and set fallback sources or content.
 * 
 * @param {Function} srcRewriter - A function that takes a providedSrc and an element, and rewrites its src to an alternate src.
 * @param {Object} args - Configuration options for the event listener.
 * @param {Function} [args.handleElement] - An optional function that takes an element and returns true if the element should be handled.
 * @param {Function} [args.targetHandler] - An optional function that takes an element and returns a handler function for that element or null for default handling.
 * @param {String} [args.dataErrorHandledAttribute='data-error-handled'] - The attribute used to mark elements that have already been handled.
 * 
 * The `true` parameter in addEventListener means the event is captured in the capturing phase,
 * allowing it to handle the error event before it reaches the target element.
 * 
 * @example
 * handleExternalRefErrors((providedSrc, target) => {
 *         // Custom logic to determine the new src based on the target element
 *         return 'path/to/custom/placeholder.png';
 *     }, {
 *       handleElement: element => {
 *           // Custom validation logic
 *           // Return true if the element should be handled, false otherwise
 *           // Example: Check if the element is inside a specific container
 *           return document.getElementById('specific-container').contains(element);
 *       },
 *       targetHandler: target => {
 *           // Return a function to handle the target if needed, or null for default handling
 *           if (target.tagName === 'IMG') {
 *               return target => {
 *                   target.src = 'path/to/image-placeholder.png';
 *               };
 *           }
 *           return null;
 *       },
 *       dataErrorHandledAttribute: 'data-error-handled'
 * });
 */
export function handleExternalRefErrors(srcRewriter, args = {}) {
    const {
        handleElement = () => true,
        targetHandler = () => null,
        dataErrorHandledAttribute = 'data-error-handled'
    } = args;

    document.addEventListener('error', function (event) {
        const target = event.target;

        if (!handleElement(target) || target.hasAttribute(dataErrorHandledAttribute)) {
            return;
        }

        // Mark the element as handled to prevent infinite loop
        target.setAttribute(dataErrorHandledAttribute, 'true');

        const handler = targetHandler(target);
        if (handler) {
            handler(target);
        } else {
            switch (target.tagName) {
                case 'IMG':
                    target.src = srcRewriter(target.src, target);
                    break;
                case 'SVG':
                    // Handle SVG fallback (e.g., setting a fallback SVG content)
                    target.outerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="gray" /></svg>`;
                    break;
                case 'PICTURE':
                    // Handle PICTURE fallback by setting the fallback for nested <source> elements
                    Array.from(target.getElementsByTagName('source')).forEach(source => {
                        source.srcset = srcRewriter(source.srcset, source);
                    });
                    break;
                case 'VIDEO':
                    // Handle VIDEO fallback by setting a placeholder video source
                    target.poster = srcRewriter(target.poster, target); // Set a poster image for the video
                    target.innerHTML = `<source src="${srcRewriter(target.src, target)}" type="video/mp4">`;
                    break;
                case 'AUDIO':
                    // Handle AUDIO fallback by setting a placeholder audio source
                    target.innerHTML = `<source src="${srcRewriter(target.src, target)}" type="audio/mpeg">`;
                    break;
                case 'OBJECT':
                    // Handle OBJECT fallback by setting a placeholder data source
                    target.data = srcRewriter(target.data, target);
                    break;
                case 'EMBED':
                    // Handle EMBED fallback by setting a placeholder src
                    target.src = srcRewriter(target.src, target);
                    break;
                case 'IFRAME':
                    // Handle IFRAME fallback by setting a placeholder src
                    target.src = srcRewriter(target.src, target);
                    break;
                default:
                    break;
            }
        }
    }, true);
}
