package org.aquilagraph.build;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The local operation catalog owns Maven goals and mandatory operation flags. */
enum BuildOperation {
  COMPILE("-Dmaven.test.skip=true compile"), TEST("test"), INTEGRATION_TEST("-DskipTests=true verify"),
  INTEGRATION_TEST_NEO4J("-DskipTests=true -Pneo4j-integration verify"), CHECK_PROCESS(focused("process")),
  CHECK_PERSISTENCE(focused("persistence")), CHECK_LIFECYCLE(focused("lifecycle")),
  COVERAGE("jacoco:prepare-agent test antrun:run@report-unit-coverage"), FORMAT("validate spotless:apply"),
  FORMAT_CHECK("validate spotless:check"), LINE_ENDINGS_CHECK(""), QUALITY("-Dmaven.test.skip=true verify"),
  SUPPLY_CHAIN_CHECK("-Dmaven.test.skip=true -Psupply-chain verify"), CLEAN("clean");

  private final String mavenArgumentsText;

  BuildOperation(String mavenArgumentsText) {
    this.mavenArgumentsText = mavenArgumentsText;
  }

  static BuildOperation parse(String value) {
    return valueOf(value.replace('-', '_').toUpperCase(Locale.ROOT));
  }

  String command() {
    return name().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  List<String> arguments() {
    return ArgumentWords.parse(mavenArgumentsText);
  }

  List<String> goals() {
    return arguments().stream().filter(value -> !value.startsWith("-")).toList();
  }

  private static String focused(String group) {
    return "-DskipTests=true -Dbuild.integration.scope=" + group + " -Dbuild.integration.groups=" + group
        + " -Dbuild.integration.includes=**/*IT.java pre-integration-test"
        + " org.apache.maven.plugins:maven-failsafe-plugin:3.6.0:integration-test"
        + " org.apache.maven.plugins:maven-failsafe-plugin:3.6.0:verify";
  }

  boolean focusedIntegration() {
    return this == CHECK_PROCESS || this == CHECK_PERSISTENCE || this == CHECK_LIFECYCLE;
  }

  Optional<String> integrationScope() {
    return switch (this) {
      case CHECK_PROCESS -> Optional.of("process");
      case CHECK_PERSISTENCE -> Optional.of("persistence");
      case CHECK_LIFECYCLE -> Optional.of("lifecycle");
      case INTEGRATION_TEST -> Optional.of("legacy-process");
      case INTEGRATION_TEST_NEO4J -> Optional.of("all");
      default -> Optional.empty();
    };
  }

  boolean checksLineEndings() {
    return this == QUALITY || this == LINE_ENDINGS_CHECK;
  }
}
