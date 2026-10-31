# java.sql API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `java/sql/Array.html` | Declaration comparison | [Document][difference-java-sql-Array] |
| `java/sql/BatchUpdateException.html` | Declaration comparison | [Document][difference-java-sql-BatchUpdateException] |
| `java/sql/Blob.html` | Declaration comparison | [Document][difference-java-sql-Blob] |
| `java/sql/CallableStatement.html` | Declaration comparison | [Document][difference-java-sql-CallableStatement] |
| `java/sql/ClientInfoStatus.html` | Declaration comparison | [Document][difference-java-sql-ClientInfoStatus] |
| `java/sql/Clob.html` | Declaration comparison | [Document][difference-java-sql-Clob] |
| `java/sql/Connection.html` | Declaration comparison | [Document][difference-java-sql-Connection] |
| `java/sql/ConnectionBuilder.html` | Declaration comparison | [Document][difference-java-sql-ConnectionBuilder] |
| `java/sql/DataTruncation.html` | Declaration comparison | [Document][difference-java-sql-DataTruncation] |
| `java/sql/DatabaseMetaData.html` | Declaration comparison | [Document][difference-java-sql-DatabaseMetaData] |
| `java/sql/Date.html` | Declaration comparison | [Document][difference-java-sql-Date] |
| `java/sql/Driver.html` | Declaration comparison | [Document][difference-java-sql-Driver] |
| `java/sql/DriverAction.html` | Declaration comparison | [Document][difference-java-sql-DriverAction] |
| `java/sql/DriverManager.html` | Declaration comparison | [Document][difference-java-sql-DriverManager] |
| `java/sql/DriverPropertyInfo.html` | Declaration comparison | [Document][difference-java-sql-DriverPropertyInfo] |
| `java/sql/JDBCType.html` | Declaration comparison | [Document][difference-java-sql-JDBCType] |
| `java/sql/NClob.html` | Declaration comparison | [Document][difference-java-sql-NClob] |
| `java/sql/ParameterMetaData.html` | Declaration comparison | [Document][difference-java-sql-ParameterMetaData] |
| `java/sql/PreparedStatement.html` | Declaration comparison | [Document][difference-java-sql-PreparedStatement] |
| `java/sql/PseudoColumnUsage.html` | Declaration comparison | [Document][difference-java-sql-PseudoColumnUsage] |
| `java/sql/Ref.html` | Declaration comparison | [Document][difference-java-sql-Ref] |
| `java/sql/ResultSet.html` | Declaration comparison | [Document][difference-java-sql-ResultSet] |
| `java/sql/ResultSetMetaData.html` | Declaration comparison | [Document][difference-java-sql-ResultSetMetaData] |
| `java/sql/RowId.html` | Declaration comparison | [Document][difference-java-sql-RowId] |
| `java/sql/RowIdLifetime.html` | Declaration comparison | [Document][difference-java-sql-RowIdLifetime] |
| `java/sql/SQLClientInfoException.html` | Declaration comparison | [Document][difference-java-sql-SQLClientInfoException] |
| `java/sql/SQLData.html` | Declaration comparison | [Document][difference-java-sql-SQLData] |
| `java/sql/SQLDataException.html` | Declaration comparison | [Document][difference-java-sql-SQLDataException] |
| `java/sql/SQLException.html` | Declaration comparison | [Document][difference-java-sql-SQLException] |
| `java/sql/SQLFeatureNotSupportedException.html` | Declaration comparison | [Document][difference-java-sql-SQLFeatureNotSupportedException] |
| `java/sql/SQLInput.html` | Declaration comparison | [Document][difference-java-sql-SQLInput] |
| `java/sql/SQLIntegrityConstraintViolationException.html` | Declaration comparison | [Document][difference-java-sql-SQLIntegrityConstraintViolationException] |
| `java/sql/SQLInvalidAuthorizationSpecException.html` | Declaration comparison | [Document][difference-java-sql-SQLInvalidAuthorizationSpecException] |
| `java/sql/SQLNonTransientConnectionException.html` | Declaration comparison | [Document][difference-java-sql-SQLNonTransientConnectionException] |
| `java/sql/SQLNonTransientException.html` | Declaration comparison | [Document][difference-java-sql-SQLNonTransientException] |
| `java/sql/SQLOutput.html` | Declaration comparison | [Document][difference-java-sql-SQLOutput] |
| `java/sql/SQLPermission.html` | Declaration comparison | [Document][difference-java-sql-SQLPermission] |
| `java/sql/SQLRecoverableException.html` | Declaration comparison | [Document][difference-java-sql-SQLRecoverableException] |
| `java/sql/SQLSyntaxErrorException.html` | Declaration comparison | [Document][difference-java-sql-SQLSyntaxErrorException] |
| `java/sql/SQLTimeoutException.html` | Declaration comparison | [Document][difference-java-sql-SQLTimeoutException] |
| `java/sql/SQLTransactionRollbackException.html` | Declaration comparison | [Document][difference-java-sql-SQLTransactionRollbackException] |
| `java/sql/SQLTransientConnectionException.html` | Declaration comparison | [Document][difference-java-sql-SQLTransientConnectionException] |
| `java/sql/SQLTransientException.html` | Declaration comparison | [Document][difference-java-sql-SQLTransientException] |
| `java/sql/SQLType.html` | Declaration comparison | [Document][difference-java-sql-SQLType] |
| `java/sql/SQLWarning.html` | Declaration comparison | [Document][difference-java-sql-SQLWarning] |
| `java/sql/SQLXML.html` | Declaration comparison | [Document][difference-java-sql-SQLXML] |
| `java/sql/Savepoint.html` | Declaration comparison | [Document][difference-java-sql-Savepoint] |
| `java/sql/ShardingKey.html` | Declaration comparison | [Document][difference-java-sql-ShardingKey] |
| `java/sql/ShardingKeyBuilder.html` | Declaration comparison | [Document][difference-java-sql-ShardingKeyBuilder] |
| `java/sql/Statement.html` | Declaration comparison | [Document][difference-java-sql-Statement] |
| `java/sql/Struct.html` | Declaration comparison | [Document][difference-java-sql-Struct] |
| `java/sql/Time.html` | Declaration comparison | [Document][difference-java-sql-Time] |
| `java/sql/Timestamp.html` | Declaration comparison | [Document][difference-java-sql-Timestamp] |
| `java/sql/Types.html` | Declaration comparison | [Document][difference-java-sql-Types] |
| `java/sql/Wrapper.html` | Declaration comparison | [Document][difference-java-sql-Wrapper] |
| `java/sql/package-summary.html` | Package comparison | [Document][difference-java-sql-package-summary] |
| `javax/sql/CommonDataSource.html` | Declaration comparison | [Document][difference-javax-sql-CommonDataSource] |
| `javax/sql/ConnectionEvent.html` | Declaration comparison | [Document][difference-javax-sql-ConnectionEvent] |
| `javax/sql/ConnectionEventListener.html` | Declaration comparison | [Document][difference-javax-sql-ConnectionEventListener] |
| `javax/sql/ConnectionPoolDataSource.html` | Declaration comparison | [Document][difference-javax-sql-ConnectionPoolDataSource] |
| `javax/sql/DataSource.html` | Declaration comparison | [Document][difference-javax-sql-DataSource] |
| `javax/sql/PooledConnection.html` | Declaration comparison | [Document][difference-javax-sql-PooledConnection] |
| `javax/sql/PooledConnectionBuilder.html` | Declaration comparison | [Document][difference-javax-sql-PooledConnectionBuilder] |
| `javax/sql/RowSet.html` | Declaration comparison | [Document][difference-javax-sql-RowSet] |
| `javax/sql/RowSetEvent.html` | Declaration comparison | [Document][difference-javax-sql-RowSetEvent] |
| `javax/sql/RowSetInternal.html` | Declaration comparison | [Document][difference-javax-sql-RowSetInternal] |
| `javax/sql/RowSetListener.html` | Declaration comparison | [Document][difference-javax-sql-RowSetListener] |
| `javax/sql/RowSetMetaData.html` | Declaration comparison | [Document][difference-javax-sql-RowSetMetaData] |
| `javax/sql/RowSetReader.html` | Declaration comparison | [Document][difference-javax-sql-RowSetReader] |
| `javax/sql/RowSetWriter.html` | Declaration comparison | [Document][difference-javax-sql-RowSetWriter] |
| `javax/sql/StatementEvent.html` | Declaration comparison | [Document][difference-javax-sql-StatementEvent] |
| `javax/sql/StatementEventListener.html` | Declaration comparison | [Document][difference-javax-sql-StatementEventListener] |
| `javax/sql/XAConnection.html` | Declaration comparison | [Document][difference-javax-sql-XAConnection] |
| `javax/sql/XAConnectionBuilder.html` | Declaration comparison | [Document][difference-javax-sql-XAConnectionBuilder] |
| `javax/sql/XADataSource.html` | Declaration comparison | [Document][difference-javax-sql-XADataSource] |
| `javax/sql/package-summary.html` | Package comparison | [Document][difference-javax-sql-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-java-sql-Array]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Array.html
[difference-java-sql-BatchUpdateException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/BatchUpdateException.html
[difference-java-sql-Blob]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Blob.html
[difference-java-sql-CallableStatement]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/CallableStatement.html
[difference-java-sql-ClientInfoStatus]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ClientInfoStatus.html
[difference-java-sql-Clob]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Clob.html
[difference-java-sql-Connection]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Connection.html
[difference-java-sql-ConnectionBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ConnectionBuilder.html
[difference-java-sql-DataTruncation]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/DataTruncation.html
[difference-java-sql-DatabaseMetaData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/DatabaseMetaData.html
[difference-java-sql-Date]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Date.html
[difference-java-sql-Driver]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Driver.html
[difference-java-sql-DriverAction]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/DriverAction.html
[difference-java-sql-DriverManager]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/DriverManager.html
[difference-java-sql-DriverPropertyInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/DriverPropertyInfo.html
[difference-java-sql-JDBCType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/JDBCType.html
[difference-java-sql-NClob]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/NClob.html
[difference-java-sql-ParameterMetaData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ParameterMetaData.html
[difference-java-sql-PreparedStatement]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/PreparedStatement.html
[difference-java-sql-PseudoColumnUsage]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/PseudoColumnUsage.html
[difference-java-sql-Ref]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Ref.html
[difference-java-sql-ResultSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ResultSet.html
[difference-java-sql-ResultSetMetaData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ResultSetMetaData.html
[difference-java-sql-RowId]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/RowId.html
[difference-java-sql-RowIdLifetime]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/RowIdLifetime.html
[difference-java-sql-SQLClientInfoException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLClientInfoException.html
[difference-java-sql-SQLData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLData.html
[difference-java-sql-SQLDataException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLDataException.html
[difference-java-sql-SQLException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLException.html
[difference-java-sql-SQLFeatureNotSupportedException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLFeatureNotSupportedException.html
[difference-java-sql-SQLInput]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLInput.html
[difference-java-sql-SQLIntegrityConstraintViolationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLIntegrityConstraintViolationException.html
[difference-java-sql-SQLInvalidAuthorizationSpecException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLInvalidAuthorizationSpecException.html
[difference-java-sql-SQLNonTransientConnectionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLNonTransientConnectionException.html
[difference-java-sql-SQLNonTransientException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLNonTransientException.html
[difference-java-sql-SQLOutput]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLOutput.html
[difference-java-sql-SQLPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLPermission.html
[difference-java-sql-SQLRecoverableException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLRecoverableException.html
[difference-java-sql-SQLSyntaxErrorException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLSyntaxErrorException.html
[difference-java-sql-SQLTimeoutException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLTimeoutException.html
[difference-java-sql-SQLTransactionRollbackException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLTransactionRollbackException.html
[difference-java-sql-SQLTransientConnectionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLTransientConnectionException.html
[difference-java-sql-SQLTransientException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLTransientException.html
[difference-java-sql-SQLType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLType.html
[difference-java-sql-SQLWarning]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLWarning.html
[difference-java-sql-SQLXML]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/SQLXML.html
[difference-java-sql-Savepoint]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Savepoint.html
[difference-java-sql-ShardingKey]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ShardingKey.html
[difference-java-sql-ShardingKeyBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/ShardingKeyBuilder.html
[difference-java-sql-Statement]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Statement.html
[difference-java-sql-Struct]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Struct.html
[difference-java-sql-Time]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Time.html
[difference-java-sql-Timestamp]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Timestamp.html
[difference-java-sql-Types]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Types.html
[difference-java-sql-Wrapper]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/Wrapper.html
[difference-java-sql-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/java/sql/package-summary.html
[difference-javax-sql-CommonDataSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/CommonDataSource.html
[difference-javax-sql-ConnectionEvent]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/ConnectionEvent.html
[difference-javax-sql-ConnectionEventListener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/ConnectionEventListener.html
[difference-javax-sql-ConnectionPoolDataSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/ConnectionPoolDataSource.html
[difference-javax-sql-DataSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/DataSource.html
[difference-javax-sql-PooledConnection]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/PooledConnection.html
[difference-javax-sql-PooledConnectionBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/PooledConnectionBuilder.html
[difference-javax-sql-RowSet]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSet.html
[difference-javax-sql-RowSetEvent]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetEvent.html
[difference-javax-sql-RowSetInternal]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetInternal.html
[difference-javax-sql-RowSetListener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetListener.html
[difference-javax-sql-RowSetMetaData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetMetaData.html
[difference-javax-sql-RowSetReader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetReader.html
[difference-javax-sql-RowSetWriter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/RowSetWriter.html
[difference-javax-sql-StatementEvent]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/StatementEvent.html
[difference-javax-sql-StatementEventListener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/StatementEventListener.html
[difference-javax-sql-XAConnection]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/XAConnection.html
[difference-javax-sql-XAConnectionBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/XAConnectionBuilder.html
[difference-javax-sql-XADataSource]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/XADataSource.html
[difference-javax-sql-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/javax/sql/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.sql/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
