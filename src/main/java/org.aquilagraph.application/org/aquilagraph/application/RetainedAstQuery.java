package org.aquilagraph.application;

import java.util.Objects;
import org.aquilagraph.application.ApplicationRepositoryOperationException.Boundary;
import org.aquilagraph.application.port.AstResultRepository;
import org.aquilagraph.application.port.AstResultRepositoryFactory;
import org.aquilagraph.identity.api.AstIdentity;

/** Owns the repository scope and returned identity check for an exact lookup. */
final class RetainedAstQuery implements AstQuery {
  private final AstResultRepositoryFactory repositories;

  RetainedAstQuery(AstResultRepositoryFactory repositories) {
    this.repositories = Objects.requireNonNull(repositories, "repositories");
  }

  @Override
  public AstLookup findAst(AstIdentity identity) {
    var context = RepositoryOperationContext.queryAst(identity);
    var owned = new OwnedRepository(context);
    return owned.use(repositories::open, repository -> {
      var result = owned.observe(Boundary.OPERATION, () -> repository.find(identity));
      return owned.observe(Boundary.VALIDATION, () -> {
        Objects.requireNonNull(result, "AST lookup");
        if (!identity.equals(result.requestedIdentity())) {
          throw new IllegalArgumentException("AST repository returned another requested identity");
        }
        return result;
      });
    }, AstResultRepository::close);
  }
}
