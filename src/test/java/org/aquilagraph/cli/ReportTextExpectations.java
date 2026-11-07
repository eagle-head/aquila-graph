package org.aquilagraph.cli;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.application.AnalysisRun;
import org.aquilagraph.identity.api.AstIdentity;

/** Independently authored channel blocks for the fixed reporting fixtures. */
final class ReportTextExpectations {
  private ReportTextExpectations() { }

  static List<String> noPublication(AnalysisRun run) {
    var result = new ArrayList<>(List.of("Publication intent: DO_NOT_SAVE"));
    var ast = run.representation().ast().result();
    if (ast.isPresent()) {
      result.addAll(astSubject("Publication", ast.orElseThrow().identity()));
    } else {
      result.add("Publication subject: unavailable");
    }
    result.add("Commit knowledge: NOT_STARTED");
    return result;
  }

  static List<String> completedPublication(AnalysisRun run, String knowledge) {
    return List.of("Publication intent: SAVE_COMPLETED", "Publication subject: COMPLETED_RUN \"" + run.identity().key() + "\"",
        "Commit knowledge: " + knowledge);
  }

  static List<String> astSubject(String prefix, AstIdentity identity) {
    return List.of(prefix + " subject: AST \"" + identity.astResultKey() + "\"",
        prefix + " software signature: \"" + identity.softwareSignature() + "\"",
        prefix + " source model: \"" + identity.sourceModelVersion() + "\"",
        prefix + " AST profile: \"" + identity.astProfileVersion() + "\"",
        prefix + " production revision: \"" + identity.semanticProductionRevision() + "\"",
        prefix + " content digest: \"" + identity.canonicalContentDigest() + "\"");
  }

  static List<String> namedInput(String description, String causeType, String causeMessage) {
    var result = new ArrayList<>(List.of("Input rejected: \"" + description + "\"", "Failure reference: 1"));
    result.addAll(node(1, "org.aquilagraph.source.api.AnalysisInputException", "\"" + description + "\"", "2",
        "not-applicable", "not-applicable", List.of(), List.of("  Operation: not-applicable")));
    result.addAll(simpleNode(2, causeType, causeMessage));
    return result;
  }

  static List<String> namedEnvironment(String description) {
    var result = new ArrayList<>(List.of("Analysis failed: \"" + description + "\"", "Failure reference: 1"));
    result.addAll(simpleNode(1, "org.aquilagraph.source.api.AnalysisEnvironmentException", description));
    return result;
  }

  static List<String> publicationFailure(AnalysisRun run, String carrierType, String boundary, String knowledge) {
    var result = new ArrayList<>(List.of("Repository save failed: \"database unavailable\"", "Failure reference: 1"));
    result.addAll(node(1, "org.aquilagraph.application.PublicationFailure", "\"Publication failed\"", "2", "2", "3", List.of(),
        List.of("  Operation: not-applicable")));
    result.addAll(node(2, carrierType, "\"Repository operation failed\"", "3", "not-applicable", "3", List.of(),
        List.of("  Operation: SAVE_COMPLETED_RUN", "  Operation subject: COMPLETED_RUN \"" + run.identity().key() + "\"",
            "  Commit knowledge: " + knowledge, "  Application boundary: " + boundary,
            "  Adapter: not-applicable", "  Adapter phase: not-applicable")));
    result.addAll(node(3, "org.aquilagraph.application.port.RepositoryException", "\"database unavailable\"", "4", "not-applicable",
        "not-applicable", List.of(), List.of("  Operation: not-applicable")));
    result.addAll(simpleNode(4, "java.lang.IllegalStateException", "offline"));
    return result;
  }

  static List<String> simpleNode(int reference, String type, String message) {
    return node(reference, type, "\"" + message + "\"", "unavailable", "not-applicable", "not-applicable", List.of(),
        List.of("  Operation: not-applicable"));
  }

  static List<String> node(int reference, String type, String message, String cause, String carrier, String primary,
      List<Integer> suppressed, List<String> operation) {
    var result = new ArrayList<>(List.of("Failure: " + reference, "  Type: \"" + type + "\"", "  Message: " + message,
        "  Cause: " + cause, "  Carrier: " + carrier, "  Effective primary: " + primary));
    if (suppressed.isEmpty()) { result.add("  Suppressed: none"); }
    else { suppressed.forEach(value -> result.add("  Suppressed: " + value)); }
    result.addAll(operation);
    return result;
  }
}
