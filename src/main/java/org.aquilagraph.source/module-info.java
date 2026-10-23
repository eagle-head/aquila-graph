/** Source ownership in the analysis pipeline. */
module org.aquilagraph.source {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  exports org.aquilagraph.source.api;
  exports org.aquilagraph.source.admission to org.aquilagraph.frontend;
  exports org.aquilagraph.source.retention to org.aquilagraph.repository;
}
