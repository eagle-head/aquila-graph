/** Frontend ownership in the analysis pipeline. */
module org.aquilagraph.frontend {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  requires transitive org.aquilagraph.source;
  requires java.compiler;
  requires static jdk.compiler;
  requires jdk.jfr;
  uses org.aquilagraph.frontend.AnalysisObserverFactory;
  exports org.aquilagraph.frontend;
}
