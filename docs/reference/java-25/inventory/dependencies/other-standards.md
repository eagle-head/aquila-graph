# Other standards and registries

[Dependency inventory](../dependencies.md)

This register transcribes the specification titles, target URLs, and every referring API locator in the Oracle
Java SE 25 and JDK 25 External Specifications index. It records declared specification relationships; the
referring API contract determines which clauses apply.

Target URLs and edition labels are retained as cited. An unversioned address is not silently replaced by a
newer edition. Each record maps the cited source and its origins; external content remains at its publisher.

Records: 8 specification entries; 15 referring API locators.

[Source index][index]

## Character Sets

Specification: [Character Sets][spec-1].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.nio.charset.Charset][spec-1-ref-1]
- [java.nio.charset.Charset.isRegistered()][spec-1-ref-2]

## FIPS 186-3 Digital Signature Standard (DSS)

Specification: [FIPS 186-3 Digital Signature Standard (DSS)][spec-2].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.security.spec.DSAGenParameterSpec][spec-2-ref-1]

## IEEE Standard for Floating-Point Arithmetic

Specification: [IEEE Standard for Floating-Point Arithmetic][spec-3].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.lang.Double][spec-3-ref-1]
- [class java.lang.Float][spec-3-ref-2]
- [class java.lang.Math][spec-3-ref-3]
- [class java.lang.StrictMath][spec-3-ref-4]
- [class java.math.BigDecimal][spec-3-ref-5]
- [class java.math.MathContext][spec-3-ref-6]
- [enum class java.math.RoundingMode][spec-3-ref-7]

## ISO - ISO 4217 - Currency codes

Specification: [ISO - ISO 4217 - Currency codes][spec-4].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.util.Currency][spec-4-ref-1]

## Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and GMAC

Specification: [Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM) and
GMAC][spec-5].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class javax.crypto.spec.GCMParameterSpec][spec-5-ref-1]

## Recommendation for Random Number Generation Using Deterministic Random Bit Generators

Specification: [Recommendation for Random Number Generation Using Deterministic Random Bit
Generators][spec-6].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.security.DrbgParameters][spec-6-ref-1]

## Security Requirements for Cryptographic Modules

Specification: [Security Requirements for Cryptographic Modules][spec-7].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [class java.security.SecureRandom][spec-7-ref-1]

## Time Zone Database

Specification: [Time Zone Database][spec-8].

Relationship: declared external specification. Status: **Source mapped**.

Referring API declarations:

- [java.util.spi.TimeZoneNameProvider.getDisplayName(String, boolean, int, Locale)][spec-8-ref-1]

## References

The links below preserve the source index targets and declaration anchors.

[index]: https://docs.oracle.com/en/java/javase/25/docs/api/external-specs.html
[spec-1]: http://www.iana.org/assignments/character-sets
[spec-1-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/charset/Charset.html#CharacterSets
[spec-1-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/charset/Charset.html#CharacterSets-1
[spec-2]: https://csrc.nist.gov/publications/fips/fips186-3/fips_186-3.pdf
[spec-2-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/DSAGenParameterSpec.html#FIPS186-3DigitalSignatureStandard(DSS)
[spec-3]: https://standards.ieee.org/ieee/754/6210/
[spec-3-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Double.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-2]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Float.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-3]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Math.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-4]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/StrictMath.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-5]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-6]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/MathContext.html#IEEEStandardforFloating-PointArithmetic
[spec-3-ref-7]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/RoundingMode.html#IEEEStandardforFloating-PointArithmetic
[spec-4]: https://www.iso.org/iso-4217-currency-codes.html
[spec-4-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Currency.html#ISO-ISO4217-Currencycodes
[spec-5]: https://csrc.nist.gov/publications/nistpubs/800-38D/SP-800-38D.pdf
[spec-5-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html#RecommendationforBlockCipherModesofOperation:Galois/CounterMode(GCM)andGMAC
[spec-6]: https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-90Ar1.pdf
[spec-6-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/DrbgParameters.html#RecommendationforRandomNumberGenerationUsingDeterministicRandomBitGenerators
[spec-7]: https://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.140-2.pdf
[spec-7-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureRandom.html#SecurityRequirementsforCryptographicModules
[spec-8]: https://www.iana.org/time-zones
[spec-8-ref-1]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/spi/TimeZoneNameProvider.html#TimeZoneDatabase
