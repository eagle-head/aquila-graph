/** Cfg ownership in the analysis pipeline. */
module org.aquilagraph.cfg {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  requires transitive org.aquilagraph.source;
  exports org.aquilagraph.cfg.api;
  exports org.aquilagraph.cfg;
}
