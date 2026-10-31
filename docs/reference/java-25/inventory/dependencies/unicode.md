# Unicode specifications

[Dependency inventory](../dependencies.md)

This register transcribes the specification titles, target URLs, and every referring API locator in the Oracle
Java SE 25 and JDK 25 External Specifications index. It records declared specification relationships; the
referring API contract determines which clauses apply.

Target URLs and edition labels are retained as cited. An unversioned address is not silently replaced by a
newer edition. Each record maps the cited source and its origins; external content remains at its publisher.

Records: 10 specification entries; 36 referring API locators.

[Source index][index]

## Unicode 3.1.0

Specification: [Unicode 3.1.0][spec-1].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.lang.Character][spec-1-ref-1]

## Unicode Character Database

Specification: [Unicode Character Database][spec-2].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.lang.Character.isUnicodeIdentifierPart(char)][spec-2-ref-1]
- [java.lang.Character.isUnicodeIdentifierPart(int)][spec-2-ref-2]
- [java.lang.Character.isUnicodeIdentifierStart(char)][spec-2-ref-3]
- [java.lang.Character.isUnicodeIdentifierStart(int)][spec-2-ref-4]

## Unicode Emoji

Specification: [Unicode Emoji][spec-3].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.lang.Character.isEmoji(int)][spec-3-ref-1]
- [java.lang.Character.isEmojiComponent(int)][spec-3-ref-2]
- [java.lang.Character.isEmojiModifier(int)][spec-3-ref-3]
- [java.lang.Character.isEmojiModifierBase(int)][spec-3-ref-4]
- [java.lang.Character.isEmojiPresentation(int)][spec-3-ref-5]
- [java.lang.Character.isExtendedPictographic(int)][spec-3-ref-6]

## Unicode Identifier and Pattern Syntax

Specification: [Unicode Identifier and Pattern Syntax][spec-4].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.lang.Character.isUnicodeIdentifierPart(char)][spec-4-ref-1]
- [java.lang.Character.isUnicodeIdentifierPart(int)][spec-4-ref-2]
- [java.lang.Character.isUnicodeIdentifierStart(char)][spec-4-ref-3]
- [java.lang.Character.isUnicodeIdentifierStart(int)][spec-4-ref-4]

## Unicode Locale Data Markup Language

Specification: [Unicode Locale Data Markup Language][spec-5].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.util.Locale][spec-5-ref-1]

## Unicode Locale Data Markup Language (LDML)

Specification: [Unicode Locale Data Markup Language (LDML)][spec-6].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.text.CompactNumberFormat][spec-6-ref-1]
- [class java.text.DecimalFormat][spec-6-ref-2]
- [class java.text.ListFormat][spec-6-ref-3]
- [class java.text.NumberFormat][spec-6-ref-4]
- [java.text.Collator.getInstance(Locale)][spec-6-ref-5]
- [java.text.CompactNumberFormat.CompactNumberFormat(String, DecimalFormatSymbols, String\[\],
String)][spec-6-ref-6]
- [java.text.DateFormatSymbols.getMonths()][spec-6-ref-7]
- [java.text.DateFormatSymbols.getShortMonths()][spec-6-ref-8]
- [java.text.NumberFormat.getCurrencyInstance(Locale)][spec-6-ref-9]
- [java.time.format.DateTimeFormatter.ofLocalizedPattern(String)][spec-6-ref-10]
- [java.time.format.DateTimeFormatterBuilder.appendDayPeriodText(TextStyle)][spec-6-ref-11]
- [java.time.format.DateTimeFormatterBuilder.appendLocalized(String)][spec-6-ref-12]

## Unicode Normalization Forms

Specification: [Unicode Normalization Forms][spec-7].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.text.Normalizer][spec-7-ref-1]
- [enum class java.text.Normalizer.Form][spec-7-ref-2]
- [java.text.Collator.CANONICAL_DECOMPOSITION][spec-7-ref-3]
- [java.text.Collator.FULL_DECOMPOSITION][spec-7-ref-4]

## Unicode Regular Expressions

Specification: [Unicode Regular Expressions][spec-8].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.util.regex.Pattern][spec-8-ref-1]
- [java.util.regex.Pattern.UNICODE_CHARACTER_CLASS][spec-8-ref-2]

## Unicode Script Property

Specification: [Unicode Script Property][spec-9].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [enum class java.lang.Character.UnicodeScript][spec-9-ref-1]

## Unicode Security Considerations

Specification: [Unicode Security Considerations][spec-10].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.net.IDN][spec-10-ref-1]

## References

The links below preserve the source index targets and declaration anchors.

[index]: https://docs.oracle.com/en/java/javase/25/docs/api/external-specs.html
[spec-1]: https://www.unicode.org/reports/tr27
[spec-1-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#Unicode3.1.0
[spec-2]: https://www.unicode.org/reports/tr44
[spec-2-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeCharacterDatabase-2
[spec-2-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeCharacterDatabase-3
[spec-2-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeCharacterDatabase
[spec-2-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeCharacterDatabase-1
[spec-3]: https://unicode.org/reports/tr51/
[spec-3-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji
[spec-3-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji-4
[spec-3-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji-2
[spec-3-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji-3
[spec-3-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji-1
[spec-3-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeEmoji-5
[spec-4]: https://www.unicode.org/reports/tr31
[spec-4-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeIdentifierandPatternSyntax-2
[spec-4-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeIdentifierandPatternSyntax-3
[spec-4-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeIdentifierandPatternSyntax
[spec-4-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html#UnicodeIdentifierandPatternSyntax-1
[spec-5]: https://unicode.org/reports/tr35/
[spec-5-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.html#UnicodeLocaleDataMarkupLanguage
[spec-6]: https://www.unicode.org/reports/tr35
[spec-6-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/CompactNumberFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/DecimalFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/ListFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/NumberFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Collator.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/CompactNumberFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)-1
[spec-6-ref-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/DateFormatSymbols.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-8]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/DateFormatSymbols.html#UnicodeLocaleDataMarkupLanguage(LDML)-1
[spec-6-ref-9]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/NumberFormat.html#UnicodeLocaleDataMarkupLanguage(LDML)-1
[spec-6-ref-10]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatter.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-6-ref-11]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#UnicodeLocaleDataMarkupLanguage(LDML)-1
[spec-6-ref-12]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/format/DateTimeFormatterBuilder.html#UnicodeLocaleDataMarkupLanguage(LDML)
[spec-7]: https://www.unicode.org/reports/tr15
[spec-7-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Normalizer.html#UnicodeNormalizationForms
[spec-7-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Normalizer.Form.html#UnicodeNormalizationForms
[spec-7-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Collator.html#UnicodeNormalizationForms
[spec-7-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Collator.html#UnicodeNormalizationForms-1
[spec-8]: https://www.unicode.org/reports/tr18
[spec-8-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html#UnicodeRegularExpressions
[spec-8-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html#UnicodeRegularExpressions-1
[spec-9]: https://www.unicode.org/reports/tr24
[spec-9-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.UnicodeScript.html#UnicodeScriptProperty
[spec-10]: https://www.unicode.org/reports/tr36
[spec-10-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/net/IDN.html#UnicodeSecurityConsiderations
