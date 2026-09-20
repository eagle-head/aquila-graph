# Calculator Analysis Target

This directory is a standalone Java 25 command-line application used as an Aquila Graph analysis
target. It is not part of the Aquila Graph application source tree or root Maven build.

The calculator supports exactly four operations on signed 32-bit integers:

- addition (`+`);
- subtraction (`-`);
- multiplication (`*`); and
- integer division (`/`).

## Build

From the Aquila Graph repository root:

```powershell
.\mvnw.cmd -f analysis-targets\calculator\pom.xml clean verify
```

## Run

Pass the left operand, quoted operator, and right operand:

```powershell
java -jar analysis-targets\calculator\target\calculator-0.1.0-SNAPSHOT.jar 8 "/" 2
```

The command writes only the calculation result to standard output on success. Usage and input errors
are written to standard error.

| Exit code | Meaning |
| ---: | --- |
| `0` | Calculation completed. |
| `1` | Arithmetic error, currently division by zero. |
| `2` | Invalid argument count, operand, or operator. |

The application has no graphical interface, web endpoint, persistence, or interactive prompt.

## Analyze with Aquila Graph

After packaging the root project, analyze the calculator without executing it:

```powershell
java -jar ..\..\target\aquila-graph-0.1.0-SNAPSHOT.jar src\main\java --release 25
```

From the repository root, the equivalent source root is
`analysis-targets\calculator\src\main\java`. Java 25 is also the default when
`--release` is omitted. Add `--persist` only while the documented Compose-managed
Neo4j instance is healthy.

## References

- [`CalculatorApplication`](src/main/java/org/example/calculator/CalculatorApplication.java), command
  arguments, terminal streams, and exit-status contract.
- [`Calculator`](src/main/java/org/example/calculator/Calculator.java), four arithmetic operations.
- [`Operation`](src/main/java/org/example/calculator/Operation.java), supported operator symbols.
- [Calculator tests](src/test/java/org/example/calculator/CalculatorApplicationTest.java), observable
  command outcomes; [target build](pom.xml), Java release and executable JAR configuration.
- [Aquila Graph Java analysis profile](../../docs/design/java-analysis.md), accepted constructs,
  diagnostics, CFG semantics, and CLI contract.
