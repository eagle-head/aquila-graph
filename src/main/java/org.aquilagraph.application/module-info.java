/** Application ownership in the analysis pipeline. */
module org.aquilagraph.application {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  requires transitive org.aquilagraph.source;
  requires transitive org.aquilagraph.cfg;
  exports org.aquilagraph.application;
  exports org.aquilagraph.application.port;
}
