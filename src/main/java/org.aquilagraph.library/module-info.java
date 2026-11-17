/** Consumer-independent composition and lifecycle of Aquila Graph. */
module org.aquilagraph.library {
  requires static org.jspecify;
  requires transitive org.aquilagraph.application;
  requires org.aquilagraph.frontend;
  requires org.aquilagraph.repository;
  requires java.net.http;
  exports org.aquilagraph.library;
}
