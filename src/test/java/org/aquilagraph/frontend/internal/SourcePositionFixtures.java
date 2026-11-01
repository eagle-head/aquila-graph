package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import javax.tools.Diagnostic;
import javax.tools.ToolProvider;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.internal.AstResultAdmission;

/** Supplies compiler source-position failures through the normal translation and admission boundaries. */
public final class SourcePositionFixtures {
  private SourcePositionFixtures() {}

  public static AstResult produce(String content, Predicate<Tree> unavailable) throws IOException {
    var snapshot = InputSnapshot.fromSources(Path.of("."), List.of(SourceFile.fromContent(Path.of("Example.java"), content)));
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var policy = JavaTranslationPolicy.legacyAnalysis();
    var baseline = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture()))
        .analyze(snapshot, configuration, policy);
    var sources = new SnapshotSourceCatalog(snapshot);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var compilation = session.analyze(session.parse(), policy);
      var source = snapshot.sourceFiles().getFirst();
      var positions = new SourcePositions() {
        @Override
        public long getStartPosition(CompilationUnitTree owner, Tree tree) {
          return unavailable.test(tree) ? Diagnostic.NOPOS
              : compilation.sourceEvidence().origin(owner, source, tree).map(SourceSpan::startOffset).orElse(Diagnostic.NOPOS);
        }

        @Override
        public long getEndPosition(CompilationUnitTree owner, Tree tree) {
          return unavailable.test(tree) ? Diagnostic.NOPOS
              : compilation.sourceEvidence().origin(owner, source, tree).map(SourceSpan::endOffsetExclusive).orElse(Diagnostic.NOPOS);
        }
      };
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), new JavaSourceEvidence(positions),
          compilation.treeInventory(), policy).translate(snapshot, compilation.units());
      var input = JavaCoverageTestSupport.withFacts(baseline, translated.value().program(), translated.value().aggregate(),
          translated.emissions().events());
      return AstResultAdmission.admit(input, policy.profile(), policy.semanticRevision());
    }
  }
}
