# java.net.http API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `java/net/http/HttpClient.Builder.html` | Declaration comparison | [Document][difference-java-net-http-HttpClient.Builder] |
| `java/net/http/HttpClient.Redirect.html` | Declaration comparison | [Document][difference-java-net-http-HttpClient.Redirect] |
| `java/net/http/HttpClient.Version.html` | Declaration comparison | [Document][difference-java-net-http-HttpClient.Version] |
| `java/net/http/HttpClient.html` | Declaration comparison | [Document][difference-java-net-http-HttpClient] |
| `java/net/http/HttpConnectTimeoutException.html` | Declaration comparison | [Document][difference-java-net-http-HttpConnectTimeoutException] |
| `java/net/http/HttpHeaders.html` | Declaration comparison | [Document][difference-java-net-http-HttpHeaders] |
| `java/net/http/HttpRequest.BodyPublisher.html` | Declaration comparison | [Document][difference-java-net-http-HttpRequest.BodyPublisher] |
| `java/net/http/HttpRequest.BodyPublishers.html` | Declaration comparison | [Document][difference-java-net-http-HttpRequest.BodyPublishers] |
| `java/net/http/HttpRequest.Builder.html` | Declaration comparison | [Document][difference-java-net-http-HttpRequest.Builder] |
| `java/net/http/HttpRequest.html` | Declaration comparison | [Document][difference-java-net-http-HttpRequest] |
| `java/net/http/HttpResponse.BodyHandler.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.BodyHandler] |
| `java/net/http/HttpResponse.BodyHandlers.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.BodyHandlers] |
| `java/net/http/HttpResponse.BodySubscriber.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.BodySubscriber] |
| `java/net/http/HttpResponse.BodySubscribers.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.BodySubscribers] |
| `java/net/http/HttpResponse.PushPromiseHandler.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.PushPromiseHandler] |
| `java/net/http/HttpResponse.ResponseInfo.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse.ResponseInfo] |
| `java/net/http/HttpResponse.html` | Declaration comparison | [Document][difference-java-net-http-HttpResponse] |
| `java/net/http/HttpTimeoutException.html` | Declaration comparison | [Document][difference-java-net-http-HttpTimeoutException] |
| `java/net/http/WebSocket.Builder.html` | Declaration comparison | [Document][difference-java-net-http-WebSocket.Builder] |
| `java/net/http/WebSocket.Listener.html` | Declaration comparison | [Document][difference-java-net-http-WebSocket.Listener] |
| `java/net/http/WebSocket.html` | Declaration comparison | [Document][difference-java-net-http-WebSocket] |
| `java/net/http/WebSocketHandshakeException.html` | Declaration comparison | [Document][difference-java-net-http-WebSocketHandshakeException] |
| `java/net/http/package-summary.html` | Package comparison | [Document][difference-java-net-http-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-java-net-http-HttpClient.Builder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpClient.Builder.html
[difference-java-net-http-HttpClient.Redirect]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpClient.Redirect.html
[difference-java-net-http-HttpClient.Version]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpClient.Version.html
[difference-java-net-http-HttpClient]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpClient.html
[difference-java-net-http-HttpConnectTimeoutException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpConnectTimeoutException.html
[difference-java-net-http-HttpHeaders]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpHeaders.html
[difference-java-net-http-HttpRequest.BodyPublisher]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpRequest.BodyPublisher.html
[difference-java-net-http-HttpRequest.BodyPublishers]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpRequest.BodyPublishers.html
[difference-java-net-http-HttpRequest.Builder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpRequest.Builder.html
[difference-java-net-http-HttpRequest]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpRequest.html
[difference-java-net-http-HttpResponse.BodyHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.BodyHandler.html
[difference-java-net-http-HttpResponse.BodyHandlers]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.BodyHandlers.html
[difference-java-net-http-HttpResponse.BodySubscriber]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.BodySubscriber.html
[difference-java-net-http-HttpResponse.BodySubscribers]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.BodySubscribers.html
[difference-java-net-http-HttpResponse.PushPromiseHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.PushPromiseHandler.html
[difference-java-net-http-HttpResponse.ResponseInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.ResponseInfo.html
[difference-java-net-http-HttpResponse]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpResponse.html
[difference-java-net-http-HttpTimeoutException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/HttpTimeoutException.html
[difference-java-net-http-WebSocket.Builder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/WebSocket.Builder.html
[difference-java-net-http-WebSocket.Listener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/WebSocket.Listener.html
[difference-java-net-http-WebSocket]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/WebSocket.html
[difference-java-net-http-WebSocketHandshakeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/WebSocketHandshakeException.html
[difference-java-net-http-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/java/net/http/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.net.http/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
