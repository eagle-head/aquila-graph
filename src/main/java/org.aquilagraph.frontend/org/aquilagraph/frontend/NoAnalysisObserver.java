package org.aquilagraph.frontend;

import java.util.Map;

enum NoAnalysisObserver implements AnalysisObserver, AnalysisObserver.Observation {
  INSTANCE;

  @Override
  public Observation start(String stage, Map<String, String> attributes) {
    return this;
  }

  @Override
  public String executionId() {
    return "";
  }

  @Override
  public String traceId() {
    return "";
  }

  @Override
  public String spanId() {
    return "";
  }

  @Override
  public void complete(Map<String, String> attributes) {
  }

  @Override
  public void event(String name, Map<String, String> attributes) {
  }

  @Override
  public void close() {
  }
}
