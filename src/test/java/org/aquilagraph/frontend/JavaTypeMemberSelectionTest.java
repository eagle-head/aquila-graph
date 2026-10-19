package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class JavaTypeMemberSelectionTest {
  @Test
  void givenCompilerSelectedInheritedMember_whenQualifierChangesToUnrelatedType_thenMembershipIsRejected() throws IOException {
    // Arrange
    var source = new SimpleJavaFileObject(URI.create("string:///MemberSelection.java"), JavaFileObject.Kind.SOURCE) {
      @Override
      public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return """
            class Base { static class Member {} }
            class Derived extends Base {}
            class Unrelated {}
            class Uses { Derived.Member inherited; Unrelated unrelated; }
            """;
      }
    };
    var compiler = ToolProvider.getSystemJavaCompiler();
    try (var fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
      var task = (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none", "--release", "25"), null,
          List.of(source));
      var unit = task.parse().iterator().next();
      task.analyze();
      var trees = Trees.instance(task);
      var semantics = new SemanticResolver(trees, task.getTypes(), task.getElements(),
          new DeclarationIdentityRegistry(trees, task.getTypes(), task.getElements(), JavaTranslationPolicy.legacyAnalysis()));
      var uses = (ClassTree) unit.getTypeDecls().get(3);
      var fields = uses.getMembers().stream().filter(VariableTree.class::isInstance).map(VariableTree.class::cast).toList();
      var inherited = (MemberSelectTree) fields.getFirst().getType();
      var unrelated = fields.get(1).getType();
      var memberPath = TreePath.getPath(unit, inherited);
      var actualQualifierPath = TreePath.getPath(unit, inherited.getExpression());
      var unrelatedQualifierPath = TreePath.getPath(unit, unrelated);

      // Act
      var actualMembership = semantics.selectsTypeMember(memberPath, actualQualifierPath);
      var unrelatedMembership = semantics.selectsTypeMember(memberPath, unrelatedQualifierPath);

      // Assert
      assertTrue(actualMembership);
      assertFalse(unrelatedMembership);
    }
  }
}
