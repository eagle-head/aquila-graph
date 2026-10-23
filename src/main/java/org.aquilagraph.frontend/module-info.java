/** Frontend ownership in the analysis pipeline. */
module org.aquilagraph.frontend {
  requires static org.jspecify;
  requires transitive org.aquilagraph.identity;
  requires transitive org.aquilagraph.source;
  requires java.compiler;
  requires static jdk.compiler;
  exports org.aquilagraph.frontend;
}
