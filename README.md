# pows: Playwright over WebSocket

pows is a host that exposes Playwright functionality through a
simple JSON-over-WebSocket protocol.

Every WS connection gets a fresh playwright context and can use
it to do commands and assertions.

Playwright has APIs for many languages, but not all. This host can be used
to add bindings for the missing languages. Most every language will have
JSON and WS libraries that can be used to integrate.

[![asciicast](https://asciinema.org/a/630656.svg)](https://asciinema.org/a/630656)

## Usage

Download a relese and start it with `java -jar <release jar file>`.
Then you are ready to connect to it. You can optionally pass in a port number to use instead of 3344.

You can also run this via Clojure cli tools: `clojure -M:run`

## Bindings

Language bindings that use pows:
- [pharo-Pows](https://github.com/tatut/pharo-Pows) for Pharo Smalltalk


## Commands

All commands are JSON objects. The first command may have an `"options"` key
that has an object that configures the context.

Commands that operate or make an assertion need a `"locator"` key which
is a string or an array of strings. For example `"div.counter"` and
`["div.counter" "nth=0"]` are valid locators. See playwright documentation
on locator strings.

Commands that do an assertion may have the key/value `"not": true` to negate
the assertion.

Responses to commands will also always be a JSON object with a `"success"` key
containing a boolean value. Assertion responses will also contain `"expected"` and
`"actual"` keys. Execution errors will contain `"error"` with a string message.

See [Locator](https://playwright.dev/java/docs/api/class-locator) and [LocatorAssertions](https://playwright.dev/java/docs/api/class-locatorassertions)
for available commands and methods (not everything is implemented).

For 0-arity calls you can pass any value, it is ignored (for now), eg. `{"locator":"button","click":1}`.
For 1-arity calls the value is used as the sole argument, eg. `{"locator":"input","fill":"type something"}`.
For higher arities the value is an array of arguments eg, `{"locator":".myfoo","hasAttribute":["data-baz","420"]}`.

Navigation is done with the `{"go": "http://...some url.."}` command. You can also
include `"options"` object that may have a `"timeout"` (milliseconds, defaults to 10000)
and `"headless"` (boolean, defaults to true).
