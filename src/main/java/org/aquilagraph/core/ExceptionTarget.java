package org.aquilagraph.core;

import java.util.List;

record ExceptionTarget(List<ExceptionHandler> handlers, boolean includeExit) {
  ExceptionTarget {
    handlers = List.copyOf(handlers);
  }
}
