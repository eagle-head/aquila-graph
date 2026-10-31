# java.sql.rowset API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `javax/sql/rowset/BaseRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-BaseRowSet] |
| `javax/sql/rowset/CachedRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-CachedRowSet] |
| `javax/sql/rowset/FilteredRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-FilteredRowSet] |
| `javax/sql/rowset/JdbcRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-JdbcRowSet] |
| `javax/sql/rowset/JoinRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-JoinRowSet] |
| `javax/sql/rowset/Joinable.html` | Declaration comparison | [Document][difference-javax-sql-rowset-Joinable] |
| `javax/sql/rowset/Predicate.html` | Declaration comparison | [Document][difference-javax-sql-rowset-Predicate] |
| `javax/sql/rowset/RowSetFactory.html` | Declaration comparison | [Document][difference-javax-sql-rowset-RowSetFactory] |
| `javax/sql/rowset/RowSetMetaDataImpl.html` | Declaration comparison | [Document][difference-javax-sql-rowset-RowSetMetaDataImpl] |
| `javax/sql/rowset/RowSetProvider.html` | Declaration comparison | [Document][difference-javax-sql-rowset-RowSetProvider] |
| `javax/sql/rowset/RowSetWarning.html` | Declaration comparison | [Document][difference-javax-sql-rowset-RowSetWarning] |
| `javax/sql/rowset/WebRowSet.html` | Declaration comparison | [Document][difference-javax-sql-rowset-WebRowSet] |
| `javax/sql/rowset/package-summary.html` | Package comparison | [Document][difference-javax-sql-rowset-package-summary] |
| `javax/sql/rowset/serial/SQLInputImpl.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SQLInputImpl] |
| `javax/sql/rowset/serial/SQLOutputImpl.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SQLOutputImpl] |
| `javax/sql/rowset/serial/SerialArray.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialArray] |
| `javax/sql/rowset/serial/SerialBlob.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialBlob] |
| `javax/sql/rowset/serial/SerialClob.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialClob] |
| `javax/sql/rowset/serial/SerialDatalink.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialDatalink] |
| `javax/sql/rowset/serial/SerialException.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialException] |
| `javax/sql/rowset/serial/SerialJavaObject.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialJavaObject] |
| `javax/sql/rowset/serial/SerialRef.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialRef] |
| `javax/sql/rowset/serial/SerialStruct.html` | Declaration comparison | [Document][difference-javax-sql-rowset-serial-SerialStruct] |
| `javax/sql/rowset/serial/package-summary.html` | Package comparison | [Document][difference-javax-sql-rowset-serial-package-summary] |
| `javax/sql/rowset/spi/SyncFactory.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-SyncFactory] |
| `javax/sql/rowset/spi/SyncFactoryException.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-SyncFactoryException] |
| `javax/sql/rowset/spi/SyncProvider.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-SyncProvider] |
| `javax/sql/rowset/spi/SyncProviderException.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-SyncProviderException] |
| `javax/sql/rowset/spi/SyncResolver.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-SyncResolver] |
| `javax/sql/rowset/spi/TransactionalWriter.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-TransactionalWriter] |
| `javax/sql/rowset/spi/XmlReader.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-XmlReader] |
| `javax/sql/rowset/spi/XmlWriter.html` | Declaration comparison | [Document][difference-javax-sql-rowset-spi-XmlWriter] |
| `javax/sql/rowset/spi/package-summary.html` | Package comparison | [Document][difference-javax-sql-rowset-spi-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-javax-sql-rowset-BaseRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/BaseRowSet.html
[difference-javax-sql-rowset-CachedRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/CachedRowSet.html
[difference-javax-sql-rowset-FilteredRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/FilteredRowSet.html
[difference-javax-sql-rowset-JdbcRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/JdbcRowSet.html
[difference-javax-sql-rowset-JoinRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/JoinRowSet.html
[difference-javax-sql-rowset-Joinable]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/Joinable.html
[difference-javax-sql-rowset-Predicate]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/Predicate.html
[difference-javax-sql-rowset-RowSetFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/RowSetFactory.html
[difference-javax-sql-rowset-RowSetMetaDataImpl]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/RowSetMetaDataImpl.html
[difference-javax-sql-rowset-RowSetProvider]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/RowSetProvider.html
[difference-javax-sql-rowset-RowSetWarning]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/RowSetWarning.html
[difference-javax-sql-rowset-WebRowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/WebRowSet.html
[difference-javax-sql-rowset-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/package-summary.html
[difference-javax-sql-rowset-serial-SQLInputImpl]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SQLInputImpl.html
[difference-javax-sql-rowset-serial-SQLOutputImpl]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SQLOutputImpl.html
[difference-javax-sql-rowset-serial-SerialArray]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialArray.html
[difference-javax-sql-rowset-serial-SerialBlob]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialBlob.html
[difference-javax-sql-rowset-serial-SerialClob]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialClob.html
[difference-javax-sql-rowset-serial-SerialDatalink]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialDatalink.html
[difference-javax-sql-rowset-serial-SerialException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialException.html
[difference-javax-sql-rowset-serial-SerialJavaObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialJavaObject.html
[difference-javax-sql-rowset-serial-SerialRef]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialRef.html
[difference-javax-sql-rowset-serial-SerialStruct]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/SerialStruct.html
[difference-javax-sql-rowset-serial-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/serial/package-summary.html
[difference-javax-sql-rowset-spi-SyncFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/SyncFactory.html
[difference-javax-sql-rowset-spi-SyncFactoryException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/SyncFactoryException.html
[difference-javax-sql-rowset-spi-SyncProvider]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/SyncProvider.html
[difference-javax-sql-rowset-spi-SyncProviderException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/SyncProviderException.html
[difference-javax-sql-rowset-spi-SyncResolver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/SyncResolver.html
[difference-javax-sql-rowset-spi-TransactionalWriter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/TransactionalWriter.html
[difference-javax-sql-rowset-spi-XmlReader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/XmlReader.html
[difference-javax-sql-rowset-spi-XmlWriter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/XmlWriter.html
[difference-javax-sql-rowset-spi-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/javax/sql/rowset/spi/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql.rowset/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
