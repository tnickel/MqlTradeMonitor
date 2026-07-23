(function () {
    'use strict';

    const tokenElement = document.querySelector('meta[name="_csrf"]');
    const headerElement = document.querySelector('meta[name="_csrf_header"]');
    if (!tokenElement || !headerElement) {
        return;
    }

    const token = tokenElement.getAttribute('content');
    const headerName = headerElement.getAttribute('content');
    const originalFetch = window.fetch.bind(window);
    const safeMethods = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

    window.fetch = function (input, init) {
        const options = Object.assign({}, init || {});
        const requestMethod = options.method || (input instanceof Request ? input.method : 'GET');
        const method = requestMethod.toUpperCase();
        const requestUrl = input instanceof Request ? input.url : String(input);
        const url = new URL(requestUrl, window.location.href);

        if (url.origin === window.location.origin && !safeMethods.has(method)) {
            const headers = new Headers(options.headers || (input instanceof Request ? input.headers : undefined));
            if (!headers.has(headerName)) {
                headers.set(headerName, token);
            }
            options.headers = headers;
        }
        return originalFetch(input, options);
    };
})();
