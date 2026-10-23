/** Repository ownership in the analysis pipeline. */
module org.aquilagraph.repository {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  requires transitive org.aquilagraph.source;
  requires transitive org.aquilagraph.cfg;
  requires transitive org.aquilagraph.application;
  requires org.neo4j.driver;
  exports org.aquilagraph.repository.neo4j;
}
