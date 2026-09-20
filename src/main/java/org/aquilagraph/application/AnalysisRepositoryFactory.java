package org.aquilagraph.application;

@FunctionalInterface
public interface AnalysisRepositoryFactory {
  AnalysisRepository open();
}
