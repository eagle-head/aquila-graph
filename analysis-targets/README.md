# Analysis Targets

This directory contains standalone source projects used as input for Aquila Graph analysis.

Each child directory:

- owns its build descriptor, source tree, tests, and executable contract;
- remains outside the Aquila Graph application `src` tree;
- is not a module of the root Maven build; and
- can be supplied to Aquila Graph through its source-root path.

These projects are versioned with the repository so analysis inputs remain reproducible, but their
classes are not Aquila Graph production classes.

## References

- [Static analysis foundation](../docs/milestones/static-analysis-foundation.md), implemented
  packages and deferred capabilities.
- [Root Maven build](../pom.xml), standalone Aquila Graph project without target modules.
