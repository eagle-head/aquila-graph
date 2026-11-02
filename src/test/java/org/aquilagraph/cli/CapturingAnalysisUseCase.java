package org.aquilagraph.cli;

import java.util.ArrayList;
import java.util.List;
import org.aquilagraph.application.AnalysisExecution;
import org.aquilagraph.application.AnalysisRequest;
import org.aquilagraph.application.port.AnalysisUseCase;

final class CapturingAnalysisUseCase implements AnalysisUseCase {
  private final AnalysisUseCase response;
  private final List<AnalysisRequest> requests = new ArrayList<>();

  CapturingAnalysisUseCase(AnalysisUseCase response) {
    this.response = response;
  }

  @Override
  public AnalysisExecution execute(AnalysisRequest request) {
    requests.add(request);
    return response.execute(request);
  }

  List<AnalysisRequest> requests() {
    return List.copyOf(requests);
  }
}
