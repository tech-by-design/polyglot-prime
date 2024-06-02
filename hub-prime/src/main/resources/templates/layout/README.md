# Layout Notes

- `prime.html` is a single-file layout with `<head>`, header, navigation,
  footer, etc. enclosed into a single file.
  - TODO: everything (menus, breadcrumbs, etc.) is "hardcoded" right now and
    needs to be turned into `record`s like `Navigation`, `NavItem`, etc. for
    type-safe definitions and easier maintenance.
- TODO: review `structured.html` for an example of how Thymeleaf suggests
  layouts are done with fragments for major sections. We should switch
  `prime.html` into more structured fragments for easier maintenance.
  - See
    [thymeleaf-layout-dialect](https://ultraq.github.io/thymeleaf-layout-dialect/)
- In `<body>` tag, add
  `th:attr="data-controller-baggage=${controllerBaggageJsonText}"` and fill out
  `controllerBaggageJsonText` in Controller Model with any JSON value that needs
  to be accessible in HTML and JavaScript after the page is rendered.
