package org.aquilagraph.frontend;

import com.sun.source.tree.CompilationUnitTree;
import java.util.List;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.JavaType;

/** Owns unfinished compiler-bearing work for one unit until detached contributions are sealed. */
final class JavaUnitTranslator {
  private final CompilationUnitTree unit;
  private final SourceFile source;
  private final JavaTypeTranslator types;
  private final JavaDeclarationProjector sourceProjection;

  JavaUnitTranslator(CompilationUnitTree unit, SourceFile source, JavaTypeTranslator types, JavaDeclarationProjector sourceProjection) {
    this.unit = unit;
    this.source = source;
    this.types = types;
    this.sourceProjection = sourceProjection;
  }

  TranslationResult<Declarations> translateDeclarations() {
    return sourceProjection.translateDeclarations(unit, source, types);
  }

  JavaDeclarationProjector.UnitTranslation projectSource() {
    return sourceProjection.translate(unit, source);
  }

  record Declarations(List<JavaType> types, List<DeclarationMemberOrder> memberOrders, List<DeclarationTypeEvidence> declarationTypes,
      boolean complete) {
    Declarations {
      types = List.copyOf(types);
      memberOrders = List.copyOf(memberOrders);
      declarationTypes = List.copyOf(declarationTypes);
    }
  }
}
