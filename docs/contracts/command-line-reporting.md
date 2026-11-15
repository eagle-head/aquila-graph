# Command-Line Reporting Contract

This document owns the CLI field layout, quoting, ordering, and normal exit
classification. The [analysis contract](analysis.md#command-line-results) owns
analysis outcomes and publication eligibility. These are interface requirements;
this document does not report a new execution or acceptance campaign.

The base layout defines summary and qualification ordering. The structured
reporting rules below add fields and override the base layout where stated,
including quoting every variable text value. The final exit boundary applies
semantic precedence after observing output. Read these sections together.

## Base layout

### Admission, invocation, and exits

Keep exactly one source root, optional single `--release <feature>` and optional
single `--persist`, existing option ordering/admission partitions, default release,
root normalization, bounded AST profile, all-bodies bounded CFG selection, and
`DO_NOT_SAVE`/`SAVE_COMPLETED` intent. No new flags or default profile change.

| Outcome | Output contract | Exit |
| --- | --- | ---: |
| Usage rejection | Empty stdout; existing rejection message then existing usage on stderr; no input-port invocation | 2 |
| Named input failure | Empty stdout; `Input rejected: {description}` on stderr | 2 |
| Named environment failure | Empty stdout; `Analysis failed: {description}` on stderr | 1 |
| Normal COMPLETE request | Structured summary and repository line; native diagnostics and qualification sections | 0 |
| Normal PARTIAL or FAILED request | Same sections, with distinct literal completion values | 1 |
| Normal REJECTED request | Same sections, retaining source/selection context where available | 2 |
| Named completed-run repository failure | Retained computed-run summary; `Repository: FAILED` on stdout; native diagnostics/reasons, then `Repository save failed: {description}` on stderr | 3 |

Direct Java null arguments and unmatched effective failures stay abrupt; do not
catch them as successful/partial command results. Preserve primary/cause/suppressed
chains. Streams remain borrowed and are never closed. Ordering is defined within
each stream only. Presentation failure may leave partial output; successful
external delivery is not promised. The root's normal nonzero-exit behavior stays
unchanged.

The declared abrupt `AnalysisDerivationException` follows this same non-returning
boundary. It retains the AST and the original `CfgDerivationException` with its
completed proper request prefix and cause; it does not become a normal FAILED
run or a returned exit code. Normal recoverable construction failure remains a
`MethodCfgState.FAILED` inside a returned run.

### Standard output

Print these labels in the following order, one line per field, with no added
blank separator lines. Retain existing value derivations for run/software/input
counts. Render numeric fields with ASCII decimal digits regardless of the JVM
format locale. A line ends with the existing platform line separator.

1. `Run: {runId}`
2. `Software version: {signature|unavailable}`
3. `Completion: {COMPLETE|PARTIAL|FAILED|REJECTED}` — actual application state;
   replace historical `COMPLETED` and the merged `INCOMPLETE` label.
4. `Java release: {feature}`
5. `Java source files: {count}`
6. `AST: {COMPLETE|INCOMPLETE|UNAVAILABLE}`
7. `AST profile: {profileVersion}`
8. `CFG: {NOT_REQUESTED|UNAVAILABLE|REJECTED|COMPLETE|PARTIAL}` — `NOT_REQUESTED`
   means no request descriptor; `UNAVAILABLE` means a request exists but no CFG
   outcome could be established, such as unbound unavailable source. These are
   presentation distinctions, not fabricated `CfgOutcome` instances.
9. `CFG profile: {quotedPolicyVersion|not-requested}`
10. `CFG graph model: {quotedGraphModelVersion|not-requested}`
11. `CFG selection: {NOT_REQUESTED|ALL_BODIES|METHODS}` — an existing-source
    explicit request uses `METHODS`.
12. `CFG binding: {astResultKey|UNBOUND|NOT_REQUESTED}` — retain the actual
    requested identity for existing-source requests, including rejected ones.
13. Zero or more `Selected method: {quotedKey}` lines, in the exact retained
    requested/resolved selection order, including duplicate/unknown keys in a
    rejected request. A present empty selection remains distinct from no request.
14. If an admitted program exists, print the existing count labels in their
    existing order: `Java types`, `Java methods`, `Analyzed method bodies`,
    `CFG operations`, `CFG nodes`, `CFG edges`. Count actual validated graphs.
15. For an admitted program, print `Method CFG: {quotedKey} {state}` in canonical
    declaration order using the application's existing `methodCoverage` state.
    Do not infer coverage from optional graph presence.
16. `Repository: {status}` — exact returned publication status for normal
    execution; explicit presentation `FAILED` for the caught legacy repository
    failure variant, without constructing an invalid domain publication value.

This supports already admitted application outcomes at the presentation seam;
it does not add CLI flags for AST-only or explicit-method requests. Keep full
typed selection/profile/outcome values until rendering. Do not use an enum's
ordinal or a record's incidental `toString()` as the protocol.

Present CFG profile and graph-model versions always use the quoted-text escaping
defined below. `CfgProfile` accepts nonblank text without normalizing unsupported
values; `JavaCfgDeriver` returns a rejected request for an incompatible profile.
Preserve those requested values, including embedded line breaks and controls,
while keeping one physical output line per field. Only an absent CFG request
prints the unquoted sentinel `not-requested`; a present value with that spelling
prints `"not-requested"`.

### Standard error and structured information

Render all native diagnostic occurrences first, in `AnalysisRun.diagnostics()`
order: source, canonical method/rejected CFG, application. Each diagnostic block
contains these lines in order:

```text
Diagnostic: {quotedDiagnosticKey}
  Occurrence: {positiveDecimal}
  Severity: {severity}
  Stage: {stage}
  Cause: {outcomeKind}
  Code: {quotedCode}
  Message: {quotedMessage}
```

`Occurrence` is the one-based position in this report's native diagnostic stream,
not a new durable diagnostic identity. Retain every slot, including equal values
and unequal values with the same key. The report maps the exact source,
request/method CFG or application list position to that display position as it
composes the established order. Never recover associations using a map keyed only
by `diagnosticKey`, object equality or the first matching value. These distinctions
are already admitted by `AnalysisRun` and asserted in its value and mapper tests.
,

Follow each block with one line per origin, preserving its order:
`  Origin: {quotedSourceFileKey} file={quotedRelativePath|unavailable} range=[{start},{end})`.
Offsets are the existing half-open character offsets, not invented line/column
numbers. Resolve file paths against captured source identities only; unresolved
file names remain explicitly unavailable while existing keys/spans survive.
For an empty origin list, print `  Origins: unavailable`.

Then render qualification reasons separately in source-assessment order, existing
CFG request/method/blocker order, and application-assessment order. Each reason
block has `Qualification: {SOURCE|CFG|APPLICATION} {typedCategory}`, followed by
`  Profile: {quotedApplicableProfile|not-applicable}` and
`  Subject: {quotedExactSubject|unavailable}`. Use the same origin-line convention;
then print `  Related diagnostic: {quotedKey} occurrence={positiveDecimal}` for
each exact linked native occurrence, using the owning-position contract defined in this contract.
A separately retained evidence value with no established native occurrence uses
the evidence block below instead of an invented occurrence number.
Render owner-provided reasons and existing typed CFG refusal/failure
states; no syntax traversal, copied qualification policy, text matching or event
deduplication is allowed. A repeated event stays repeated; an application reason
referring to a domain cause is labeled as a qualification relationship, not a new
producer diagnostic. No blocking-reason block is emitted for a complete assessment.

**Selected CFG mapping:** preserve the existing request, method and blocker
variants; do not convert each into a fabricated `CfgBlocker`.

| Existing value | Qualification category and subject | Additional information and diagnostic association |
| --- | --- | --- |
| `CfgOutcome` with `REJECTED` | `REJECTED`; subject is the exact requested AST result key | One request reason with all request diagnostic slots in their retained order; no method outcome or blocker is invented. |
| Each `CfgBlocker` in a refused method | Its actual `kind`; subject is its `methodKey` | Preserve `astIdentity`, optional `occurrence`, exact `slot`, primary diagnostic slot and optional separately retained `evidenceDiagnostic`. Emit every blocker, including multiple causes in one method. |
| `MethodCfgOutcome` with `FAILED` | `FAILED`; subject is its `methodKey` | Preserve the actual failure and body association; link every method diagnostic slot. A FAILED method has no blockers. |

Every CFG reason uses the requested CFG profile and prints
`  AST result: {quotedAstResultKey}` after `Subject`. Blocker reasons then print
`  Source occurrence: {quotedNodeKey|unavailable}` and `  Slot: {quotedSlot}`.
Their origins come from the primary diagnostic. FAILED reasons instead print
`  Failure type: {quotedExceptionClassName}` and
`  Failure message: {quotedMessage|unavailable}`, using the retained effective
cause, followed by its method diagnostics' origins in list order. Request reasons
use their request diagnostics' origins in list order. Preserve repeated origins;
do not infer coordinates from messages. These extra fields precede origin and
related-diagnostic lines. Existing typed values survive through the report; the
renderer does not reclassify failure severity or reconstruct refusal policy.
,

For `CfgBlocker.evidenceDiagnostic`, append this separately labeled block inside
the reason, after the primary related-diagnostic lines:

```text
  Evidence diagnostic: {quotedDiagnosticKey}
    Severity: {severity}
    Stage: {stage}
    Cause: {outcomeKind}
    Code: {quotedCode}
    Message: {quotedMessage}
```

Use four-space-indented `Origin`/`Origins` lines for that value. This evidence has
no `Occurrence` field: the current blocker admits an originating frontend error
without requiring membership in `AnalysisRun.diagnostics()`. Do not append it to
the native event stream, relabel it CONTROL_FLOW, or assume equality proves a
particular source occurrence. Source/application reasons with separately retained
evidence use the same labeled value layout. Existing AVAILABLE/NOT_APPLICABLE
methods acquire no synthetic blockers; their native diagnostics and applicable
application qualification reasons remain visible.

Quoted text values use double quotes, escaping backslash, quote, CR, LF and tab as
`\\`, `\"`, `\r`, `\n`, `\t`; other U+0000-U+001F/U+007F controls use
four-digit `\uXXXX`. Other characters retain their text. This keeps multi-line
messages and path/key delimiters unambiguous without introducing a JSON/API surface
or changing the underlying diagnostic message. Existing usage/input/environment/
repository failure lines retain their declared wording. No interstream order is
promised.

## Successor text contract

The following structured reporting fields and common quoting rules complete the
base layout. They change presentation without adding domain policies.

### Common field rules

Keep stdout and stderr separate, existing label order, two-space nesting per
level, and no added blank separator lines. Every emitted field occupies one
physical line and uses the captured platform terminator. All variable text is
double-quoted exactly once: escape backslash, double quote, CR, LF and tab as in
the base layout, and remaining U+0000-U+001F/U+007F as four-digit `\uXXXX`.
Other text is retained. This also quotes variable run/software/profile/binding
strings and operational/argument data previously printed directly. Numeric
fields and closed literal enum tokens remain unquoted. Missing text uses the
applicable unquoted sentinel, distinct from a quoted literal with that spelling.
No domain record's incidental `toString()` is a protocol.

### Standard output

Keep the native summary fields and their order through `Repository: {status}`.
Immediately append `Publication intent: {intent}`, then
`Publication subject: {COMPLETED_RUN quotedRunId|AST quotedAstKey|unavailable}`,
then `Commit knowledge: {knowledge|unavailable}`. For an AST subject, directly
after its subject line add the exact qualification tuple in this order:
`Publication software signature`, `Publication source model`,
`Publication AST profile`, `Publication production revision`, and
`Publication content digest`, each followed by its quoted original value.
An absent subject creates none of those tuple fields. No-request, ineligible and
saved cases retain their authoritative subject/knowledge availability.

Usage/input/environment failures still have empty stdout. No emission-status
footer is appended to either channel; the invocation returns observations separately.

### Qualification and evidence blocks on standard error

In each native diagnostic block, immediately after `Occurrence` and before
`Severity`, add `  Owner: {SOURCE|CFG_REQUEST|CFG_METHOD|APPLICATION}`, followed
by `  Native position: {zeroBasedDecimal}`. For `CFG_METHOD`, append
`  Owner method: {quotedMethodKey}` immediately after the position. The other
owners are the single source, rejected request or application diagnostic list
of this report; they have no invented method identifier. Keep the full detached
owner descriptor in the semantic report. `Occurrence` remains the one-based
catalog position, independently of the native zero-based position.

Keep native diagnostics first and source, CFG, then application qualification
blocks in their contracted order. In each SOURCE qualification block, add
`  Role: {reasonRole}` immediately after `Profile`. Retain `Subject`, then append
`  Subject kind: {CAPTURE|NODE|DECLARATION|CONTEXT|FACT}` and these exact components
before the existing origin/reference/evidence fields:

| Subject form | Additional ordered fields |
| --- | --- |
| CAPTURE | None; the existing Subject remains `unavailable`. |
| NODE | `Subject source file` (quoted); the existing Subject is the quoted node key. |
| DECLARATION | None; the existing Subject is the quoted declaration key. |
| CONTEXT | `Subject source file` (quoted), `Subject partition` (token), then one `Subject owner step: {role} {ordinal}` per ordered step, or `Subject owner path: ROOT` for an empty path, followed by `Subject role` (quoted) and `Subject ordinal` (decimal). Preserve the existing compound Subject display as well. |
| FACT | `Subject fact kind` (token), `Subject source file` (quoted), and `Subject fact origin: {quotedFileKey range=[start,end)\|unavailable}`; the existing Subject is the quoted fact identity. |

These rows define distinct existing shapes, not combinations of analysis results.
Use the same typed component formatting for captured identities and paths; do
not split a display string to recover its components. Source evidence adds
`    Evidence role: {role}` immediately below `  Evidence diagnostic`, before
its existing severity/stage/cause/code/message/origin fields. Evidence without a
native role prints `not-applicable` there, and still acquires no Occurrence.

Immediately below each SOURCE `Related diagnostic` line, add the address retained
on that exact source reference. A present address emits, at four-space indentation,
`Emission producer: {quotedProducer}`, `Emission owner: {quotedOwner}`, then
`Emission ordinal: {decimal}`. An absent address emits only
`    Emission: unavailable`. Do not append these fields to other owners' links or
evidence, which expose no such source-reference address. Preserve repeated links
and their separate address observations.

Keep failed-CFG `Failure type` and `Failure message` fields, and append
`  Failure reference: {localId}` immediately after them. The values come from
the already captured root node. Existing AST/blocker and native event
relationships remain in their original owning block.

For every CFG failed-method or blocker reason, insert the method-body fields below
immediately after `AST result`, before its existing failure or blocker fields.
For APPLICATION `METHOD` and `METHOD_DIAGNOSTIC` reasons, insert the method's
`  AST result: {quotedAstResultKey}` after `Subject`, then these same body fields
before origins and event references. Request-only and source reasons gain no
method-body fields.

| Admitted method body association | Ordered fields at two-space indentation |
| --- | --- |
| Absent | `Body: unavailable` only; do not claim that absence alone proves a bodyless declaration. |
| Written | `Body: WRITTEN`, then `Body occurrence: {quotedSourceOccurrenceKey}`. |
| Nonwritten | `Body: NONWRITTEN`, then `Body origin: {quotedOrigin}`, `Body source file: {quotedSourceFileKey}`, and `Body owner declaration: {quotedDeclarationKey}`. |

The surrounding method subject and AST-result field identify the text's binding;
the semantic report retains the full `AstIdentity` tuple. Body occurrence is not
the existing blocker `Source occurrence` field. The nonwritten origin is raw data,
not a renderer classification rule. No body is reconstructed from a graph or
from the presence/absence of diagnostic origins.

### Usage and operational messages

Usage rejection still prints its one rejection line followed by the unchanged
usage line. Preserve fixed wording from the parser contract. Quote only its raw
variable operands: `Invalid Java release: {quotedRawValue}`,
`Unknown option: {quotedToken}`, and `Invalid source root: {quotedRawRoot}`.
Duplicate/missing-option and root-count messages keep their fixed text. Unsupported
release wording comes from the existing Java release contract and uses the
retained parsed feature; retain the raw value in the semantic rejection too.

Named input/environment lines keep `Input rejected:`/`Analysis failed:` followed
by the quoted captured description, then `Failure reference: {localId}`.
Publication failure keeps its existing final operational line
`Repository save failed: {quotedEffectivePrimaryDescription}`, followed by
`Failure reference: {localId}` for the publication failure root. Its diagnostic
and qualification blocks still precede that line. Usage rejection has no invented
throwable graph merely because parsing internally used an exception.

### Failure catalog

After all existing stderr blocks/messages and their root references, emit one
catalog in ascending local-ID order, with no duplicate node definitions:

```text
Failure: {localId}
  Type: {quotedClassName}
  Message: {quotedMessage|unavailable}
  Cause: {localId|unavailable}
  Carrier: {localId|not-applicable}
  Effective primary: {localId|not-applicable}
  Suppressed: {localId}
  Operation: {kind|not-applicable}
```

Repeat `Suppressed` in the original edge order; an empty list emits
`  Suppressed: none`. A present operation appends `Operation subject` with the
same typed subject/qualification-tuple layout above (using the `Operation`
prefix), then `Commit knowledge: {knowledge|unavailable}`,
`Application boundary: {boundary|not-applicable}`,
`Adapter: {quotedAdapter|not-applicable}` and
`Adapter phase: {quotedPhase|not-applicable}`, all at the node's indentation.
An absent operation has none of these children. Adapter and phase are typed
descriptor fields rendered as data, never classification keys. Reference edges
may point forward or form cycles; their node definitions are finite and unique.
An empty graph emits no catalog. This catalog is a description of the command's
existing failures; output failures do not mutate it.

## Exit and process boundary

Keep semantic outcome classification separate from emission:

- Preserve semantic `1`, `2`, or `3` for every normally returned emission result,
  including observed output failure. Publication failure retains its existing
  precedence.
- For semantic `0`, return `4` if either required channel failed; return `0` only
  when every required channel completed without observed failure and every other
  channel is explicitly unused.
- Reject an inconsistent ordinary result containing unexplained unattempted work;
  never assign it `0`. Abrupt projection, formatting, or destination failure does
  not return an ordinary invocation result or enter this normal exit mapping.

Only `AquilaGraphApplication.main` decides JVM termination, using the normally
returned invocation's exit code and the existing nonzero-only exit behavior.
It does not retry reporting or analysis when presentation fails. An abrupt
failure remains abrupt; no specific normal code is fabricated for it.

## References

- [Analysis contract](analysis.md#command-line-results), command results and native diagnostic associations.
- [CommandDocumentComposer](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandDocumentComposer.java), `compose`, detached report preparation.
- [CommandRunner](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandRunner.java), `run`, preparation and channel emission.
- [CommandExitPolicy](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/cli/CommandExitPolicy.java), `classify`, normal exit precedence.
- [AquilaGraphApplication](../../src/main/java/org.aquilagraph.cli/org/aquilagraph/AquilaGraphApplication.java), `main`, JVM termination boundary.
