package org.aquilagraph.repository.neo4j;

import java.io.IOException;
import org.aquilagraph.source.api.AstOutcome;

/** Runs the same repository failure contract with real declaration-owned source values. */
class Neo4jDeclarationAstFailureTest extends Neo4jAstResultRepositoryTest {
  @Override
  AstOutcome produce() throws IOException {
    return DeclarationAstRetentionFixture.mixed(directory);
  }
}
