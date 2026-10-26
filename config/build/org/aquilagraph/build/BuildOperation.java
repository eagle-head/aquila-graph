package org.aquilagraph.build;

import java.util.List;
import java.util.Locale;

/** The local operation catalog owns Maven goals and mandatory operation flags. */
enum BuildOperation {
  COMPILE("-Dmaven.test.skip=true compile"), TEST("test"), INTEGRATION_TEST("-DskipTests=true verify"),
  INTEGRATION_TEST_NEO4J("-DskipTests=true -Pneo4j-integration verify"),
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

  boolean checksLineEndings() {
    return this == QUALITY || this == LINE_ENDINGS_CHECK;
  }
}
