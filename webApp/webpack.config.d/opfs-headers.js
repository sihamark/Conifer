// SQLite's OPFS backend (used by the sqlite-web worker) requires the page to be cross-origin
// isolated, which in turn requires these two response headers. Set them on the webpack dev server
// so `./gradlew :webApp:wasmJsBrowserDevelopmentRun` can use OPFS persistence.
// NOTE: a production host serving the built distribution must send the same two headers.
// Plain statements rather than an IIFE: Kotlin 2.4.20's generated config ends its last statement
// without a semicolon, so a fragment starting with "(" is parsed as a call on it.
config.devServer = config.devServer || {};
config.devServer.headers = Object.assign({}, config.devServer.headers, {
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "require-corp",
});
