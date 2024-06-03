# `prime.html` Thymeleaf Layout

## Overview

`prime.html` is a single-file TailwindCSS Thymeleaf natural template layout that
encapsulates the `<head>`, header, navigation, footer, and other essential
components into a single cohesive file. This layout streamlines the process of
building web pages by providing a consistent structure and style across the
entire application.

## Navigation Menu

### Creating Main Navigation Menus

In your HTML, define the main navigation menus within a `<div>` with a specific
structure. Each menu item should be an `<a>` tag with the appropriate `href`
attribute.

```html
<div id="nav-prime" class="hidden md:block">
    <div class="ml-10 flex items-baseline space-x-4">
        <a th:href="@{/}" class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium">Welcome</a>
        <a th:href="@{/admin/observe/interactions}" class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium">Interactions</a>
        <a th:href="@{/admin/observe/sessions}" class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium">Diagnostics</a>
        <a th:href="@{/docs}" class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium">Documentation</a>
        <a th:href="@{/actuator}" class="text-gray-300 hover:bg-gray-700 hover:text-white rounded-md px-3 py-2 text-sm font-medium">Support</a>
    </div>
</div>
```

### Automatic Highlighting of Active Menu Item

A JavaScript function at the start of the page ensures that the current page's
menu item is highlighted. This function compares the current URL with the `href`
attributes of the menu items and applies the active class to the matching menu
item.

```javascript
document.addEventListener("DOMContentLoaded", () => {
  const currentPath = window.location.pathname;
  document.querySelectorAll("#nav-prime a").forEach((item) => {
    if (item.getAttribute("href") === currentPath) {
      item.classList.add("bg-gray-900", "text-white");
      item.classList.remove(
        "text-gray-300",
        "hover:bg-gray-700",
        "hover:text-white",
      );
    }
  });
});
```

## Breadcrumbs

### Automated Breadcrumbs

Breadcrumb navigation is automated using `<link rel="breadcrumb">` elements in
the `<head>` section of your HTML. The breadcrumb navigation structure is
defined in a hidden `<nav>` element, which is dynamically populated and
displayed based on the current page and the breadcrumb links.

### Breadcrumbs Definition in `<head>`

Define breadcrumbs using `<link>` elements with `rel="breadcrumb"`, `href`, and
`title` attributes. Only include inner breadcrumbs, `Home` and active page are
fixed by business rule. This means if you don't have any <link rel="breadcrumb">
the breadcrumbs will be automatically generated for `Home` and the current page.

```html
<head>
    <title>Current Page Title</title>

    <!-- only include inner breadcrumbs, `Home` and active page are fixed -->
    <link rel="breadcrumb" href="/projects" title="Projects" />
    <link rel="breadcrumb" href="/projects/details" title="Details" />
</head>
```

### Breadcrumbs HTML Structure

The `<nav>` element contains breadcrumb templates for the home, inner, and
terminal items. This structure is hidden by default and displayed by JavaScript
after page load.

The `class` and any internal tags (like chevrons, etc.) are not relevant since
the breadcrumbs JavaScript will just do structural copies and replacement of
`href` and `innerText`. This allows the Thymeleaf template to be natural and
preview-friendly in any browser.

```html
<nav id="nav-breadcrumb" aria-label="Breadcrumb">
    <ol class="inline-flex items-center space-x-1 md:space-x-2 rtl:space-x-reverse">
        <li id="breadcrumb-home" class="inline-flex items-center">
            <a href="/">Home</a>
        </li>
        <li id="breadcrumb-inner" class="hidden">
            <a href="/inner">Inner</a>
        </li>
        <li id="breadcrumb-terminal" aria-current="page">
            <a href="/inner/terminal">Terminal</a>
        </li>
    </ol>
</nav>
```

### JavaScript for Breadcrumbs

The JavaScript dynamically populates and displays the breadcrumb navigation
based on the defined rules.

```javascript
document.addEventListener("DOMContentLoaded", () => {
  const breadcrumbNav = document.getElementById("nav-breadcrumb");
  const breadcrumbList = breadcrumbNav.querySelector("ol");
  const breadcrumbInnerTemplate = document.getElementById("breadcrumb-inner");
  const breadcrumbTerminal = document.getElementById("breadcrumb-terminal");
  const linkBreadcrumbs = document.querySelectorAll("link[rel='breadcrumb']");
  const currentPath = window.location.pathname;

  if (linkBreadcrumbs.length) {
    breadcrumbInnerTemplate.remove();
    linkBreadcrumbs.forEach((link) => {
      const listItem = breadcrumbInnerTemplate.cloneNode(true);
      listItem.querySelector("a").href = link.getAttribute("href");
      listItem.querySelector("a").textContent = link.getAttribute("title");
      listItem.classList.remove("hidden");
      breadcrumbList.insertBefore(listItem, breadcrumbTerminal);
    });
    breadcrumbTerminal.querySelector("span").textContent = document.title;
    breadcrumbNav.classList.remove("hidden");
  } else if (currentPath !== "/") {
    breadcrumbTerminal.querySelector("span").textContent = document.title;
    breadcrumbInnerTemplate.remove();
    breadcrumbNav.classList.remove("hidden");
  }
});
```

## Controller Baggage

In the `<body>` tag, add the attribute
`th:attr="data-controller-baggage=\${controllerBaggageJsonText}"`. This
attribute allows you to pass JSON data from the controller to the HTML and
JavaScript after the page is rendered. Populate the `controllerBaggageJsonText`
in the Controller Model with any JSON value that needs to be accessible on the
client side.

```html
<body th:attr="data-controller-baggage=${controllerBaggageJsonText}">
    <!-- Page content -->
</body>
```

#### Baggage in Footer for Sandbox/Devl Profiles

The footer provides a way to conditionally display baggage information, such as
for a development profile. The baggage information is passed from the server to
the client and can be made visible conditionally. The footer includes the
version information and a hidden container that can be made visible to show the
baggage information.

```html
<main>
    <div class="mx-auto max-w-9xl py-2 sm:px-6 lg:px-8">
        <div class="w-full mx-auto bg-white p-8 rounded-lg shadow-lg">
            <div layout:fragment="content"></div>
        </div>

        <div class="w-full mt-8 mx-auto bg-gray-100 p-8 rounded-lg shadow-lg">
            <p>v<span th:text="\${baggage.public.appVersion}">X.Y.Z</span></p>
            <div id="devl_exposure_container" style="display: none">
                <code>document.body.dataset.controllerBaggageJsonText</code>:
                <json-viewer id="devl_controllerBaggage_userAgentBaggageExposureEnabledJsonViewer"></json-viewer>
                <p>The above is visible because <code>document.body.dataset.controllerBaggageJsonText</code> was
                    sent via
                    <code>&lt;body th:attr="data-controller-baggage-json-text=\${controllerBaggageJsonText}"&gt;</code>
                    from the server.
                </p>
            </div>
        </div>
    </div>
</main>
```

- **Version Information**: The version information of the application is
  displayed using the `baggage.public.appVersion` attribute.
- **Conditional Baggage Visibility**: The `devl_exposure_container` is hidden by
  default (`style="display: none"`). It can be made visible based on certain
  conditions, such as being in a development profile.
- **Displaying Baggage Information**: When visible, the
  `devl_exposure_container` shows the `controllerBaggageJsonText` passed from
  the server. The content is displayed using a `<json-viewer>` element for
  better readability.

This structure allows you to easily make debugging information available during
development while keeping it hidden in production environments.

