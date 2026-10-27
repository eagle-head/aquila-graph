package org.aquilagraph.build;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.build.IntegrationCatalog.Entry;
import org.aquilagraph.build.IntegrationCatalog.Kind;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

/** Test-runtime discovery bridge; the local execution implementation remains JDK-only. */
public final class IntegrationCatalogProbe {
  private IntegrationCatalogProbe() {
  }

  public static void main(String[] arguments) throws Exception {
    var root = Path.of(arguments[0]).toRealPath();
    var nonce = System.getenv("AQUILA_BUILD_INVOCATION");
    if (nonce == null) {
      throw new IllegalStateException("Integration discovery requires an admitted build invocation");
    }
    var request = LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(Path.of(arguments[1]))))
        .filters(ClassNameFilter.includeClassNamePatterns(".*IT"))
        .configurationParameter("junit.platform.execution.listeners.deactivate", "*")
        .build();
    try (var session = LauncherFactory.openSession()) {
      var catalog = catalog(session.getLauncher().discover(request), arguments[2], arguments.length > 3 ? arguments[3] : "");
      catalog.write(IntegrationCatalog.directory(root, nonce).resolve("catalog.properties"));
      System.out.println("Discovered integration identities: " + catalog.entries().size() + "; selected: " + catalog.selected().size()
          + "; scope: " + catalog.scope());
    }
  }

  static IntegrationCatalog catalog(TestPlan plan, String scope, String classes) {
    var entries = new ArrayList<Entry>();
    for (var root : plan.getRoots()) {
      for (var identifier : plan.getDescendants(root)) {
        if (identifier.getSource().orElse(null) instanceof ClassSource) {
          group(identifier);
        }
        if (!(identifier.getSource().orElse(null) instanceof MethodSource)) {
          continue;
        }
        var owner = owner(plan, identifier);
        var group = group(identifier);
        if (!group.equals(group(owner))) {
          throw new IllegalArgumentException("Integration test/factory ownership differs from its concrete class: " + identifier.getUniqueId());
        }
        var type = ((ClassSource) owner.getSource().orElseThrow()).getJavaClass();
        var platform = type.getAnnotation(EnabledOnOs.class);
        var platformSkipped = platform != null && Set.of(platform.value()).stream().noneMatch(os -> os.isCurrentOs());
        entries.add(new Entry(identifier.getUniqueId(), type.getName(), group,
            identifier.isTest() ? Kind.TEST : Kind.CONTAINER, platformSkipped));
      }
    }
    entries.sort(Comparator.comparing(Entry::id));
    return new IntegrationCatalog(scope, classes, entries);
  }

  private static String group(TestIdentifier identifier) {
    return IntegrationCatalog.group(identifier.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toSet()));
  }

  private static TestIdentifier owner(TestPlan plan, TestIdentifier identifier) {
    var current = identifier;
    var owner = identifier;
    while (plan.getParent(current).isPresent()) {
      current = plan.getParent(current).orElseThrow();
      if (current.getSource().orElse(null) instanceof ClassSource) {
        owner = current;
      }
    }
    if (!(owner.getSource().orElse(null) instanceof ClassSource)) {
      throw new IllegalArgumentException("Integration method has no concrete class owner: " + identifier.getUniqueId());
    }
    return owner;
  }
}
