# java.rmi API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `java/rmi/AccessException.html` | Declaration comparison | [Document][difference-java-rmi-AccessException] |
| `java/rmi/AlreadyBoundException.html` | Declaration comparison | [Document][difference-java-rmi-AlreadyBoundException] |
| `java/rmi/ConnectException.html` | Declaration comparison | [Document][difference-java-rmi-ConnectException] |
| `java/rmi/ConnectIOException.html` | Declaration comparison | [Document][difference-java-rmi-ConnectIOException] |
| `java/rmi/MarshalException.html` | Declaration comparison | [Document][difference-java-rmi-MarshalException] |
| `java/rmi/MarshalledObject.html` | Declaration comparison | [Document][difference-java-rmi-MarshalledObject] |
| `java/rmi/Naming.html` | Declaration comparison | [Document][difference-java-rmi-Naming] |
| `java/rmi/NoSuchObjectException.html` | Declaration comparison | [Document][difference-java-rmi-NoSuchObjectException] |
| `java/rmi/NotBoundException.html` | Declaration comparison | [Document][difference-java-rmi-NotBoundException] |
| `java/rmi/RMISecurityException.html` | Declaration comparison | [Document][difference-java-rmi-RMISecurityException] |
| `java/rmi/RMISecurityManager.html` | Declaration comparison | [Document][difference-java-rmi-RMISecurityManager] |
| `java/rmi/Remote.html` | Declaration comparison | [Document][difference-java-rmi-Remote] |
| `java/rmi/RemoteException.html` | Declaration comparison | [Document][difference-java-rmi-RemoteException] |
| `java/rmi/ServerError.html` | Declaration comparison | [Document][difference-java-rmi-ServerError] |
| `java/rmi/ServerException.html` | Declaration comparison | [Document][difference-java-rmi-ServerException] |
| `java/rmi/ServerRuntimeException.html` | Declaration comparison | [Document][difference-java-rmi-ServerRuntimeException] |
| `java/rmi/StubNotFoundException.html` | Declaration comparison | [Document][difference-java-rmi-StubNotFoundException] |
| `java/rmi/UnexpectedException.html` | Declaration comparison | [Document][difference-java-rmi-UnexpectedException] |
| `java/rmi/UnknownHostException.html` | Declaration comparison | [Document][difference-java-rmi-UnknownHostException] |
| `java/rmi/UnmarshalException.html` | Declaration comparison | [Document][difference-java-rmi-UnmarshalException] |
| `java/rmi/dgc/DGC.html` | Declaration comparison | [Document][difference-java-rmi-dgc-DGC] |
| `java/rmi/dgc/Lease.html` | Declaration comparison | [Document][difference-java-rmi-dgc-Lease] |
| `java/rmi/dgc/VMID.html` | Declaration comparison | [Document][difference-java-rmi-dgc-VMID] |
| `java/rmi/dgc/package-summary.html` | Package comparison | [Document][difference-java-rmi-dgc-package-summary] |
| `java/rmi/package-summary.html` | Package comparison | [Document][difference-java-rmi-package-summary] |
| `java/rmi/registry/LocateRegistry.html` | Declaration comparison | [Document][difference-java-rmi-registry-LocateRegistry] |
| `java/rmi/registry/Registry.html` | Declaration comparison | [Document][difference-java-rmi-registry-Registry] |
| `java/rmi/registry/RegistryHandler.html` | Declaration comparison | [Document][difference-java-rmi-registry-RegistryHandler] |
| `java/rmi/registry/package-summary.html` | Package comparison | [Document][difference-java-rmi-registry-package-summary] |
| `java/rmi/server/ExportException.html` | Declaration comparison | [Document][difference-java-rmi-server-ExportException] |
| `java/rmi/server/LoaderHandler.html` | Declaration comparison | [Document][difference-java-rmi-server-LoaderHandler] |
| `java/rmi/server/LogStream.html` | Declaration comparison | [Document][difference-java-rmi-server-LogStream] |
| `java/rmi/server/ObjID.html` | Declaration comparison | [Document][difference-java-rmi-server-ObjID] |
| `java/rmi/server/Operation.html` | Declaration comparison | [Document][difference-java-rmi-server-Operation] |
| `java/rmi/server/RMIClassLoader.html` | Declaration comparison | [Document][difference-java-rmi-server-RMIClassLoader] |
| `java/rmi/server/RMIClassLoaderSpi.html` | Declaration comparison | [Document][difference-java-rmi-server-RMIClassLoaderSpi] |
| `java/rmi/server/RMIClientSocketFactory.html` | Declaration comparison | [Document][difference-java-rmi-server-RMIClientSocketFactory] |
| `java/rmi/server/RMIFailureHandler.html` | Declaration comparison | [Document][difference-java-rmi-server-RMIFailureHandler] |
| `java/rmi/server/RMIServerSocketFactory.html` | Declaration comparison | [Document][difference-java-rmi-server-RMIServerSocketFactory] |
| `java/rmi/server/RMISocketFactory.html` | Declaration comparison | [Document][difference-java-rmi-server-RMISocketFactory] |
| `java/rmi/server/RemoteCall.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteCall] |
| `java/rmi/server/RemoteObject.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteObject] |
| `java/rmi/server/RemoteObjectInvocationHandler.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteObjectInvocationHandler] |
| `java/rmi/server/RemoteRef.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteRef] |
| `java/rmi/server/RemoteServer.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteServer] |
| `java/rmi/server/RemoteStub.html` | Declaration comparison | [Document][difference-java-rmi-server-RemoteStub] |
| `java/rmi/server/ServerCloneException.html` | Declaration comparison | [Document][difference-java-rmi-server-ServerCloneException] |
| `java/rmi/server/ServerNotActiveException.html` | Declaration comparison | [Document][difference-java-rmi-server-ServerNotActiveException] |
| `java/rmi/server/ServerRef.html` | Declaration comparison | [Document][difference-java-rmi-server-ServerRef] |
| `java/rmi/server/Skeleton.html` | Declaration comparison | [Document][difference-java-rmi-server-Skeleton] |
| `java/rmi/server/SkeletonMismatchException.html` | Declaration comparison | [Document][difference-java-rmi-server-SkeletonMismatchException] |
| `java/rmi/server/SkeletonNotFoundException.html` | Declaration comparison | [Document][difference-java-rmi-server-SkeletonNotFoundException] |
| `java/rmi/server/SocketSecurityException.html` | Declaration comparison | [Document][difference-java-rmi-server-SocketSecurityException] |
| `java/rmi/server/UID.html` | Declaration comparison | [Document][difference-java-rmi-server-UID] |
| `java/rmi/server/UnicastRemoteObject.html` | Declaration comparison | [Document][difference-java-rmi-server-UnicastRemoteObject] |
| `java/rmi/server/Unreferenced.html` | Declaration comparison | [Document][difference-java-rmi-server-Unreferenced] |
| `java/rmi/server/package-summary.html` | Package comparison | [Document][difference-java-rmi-server-package-summary] |
| `javax/rmi/ssl/SslRMIClientSocketFactory.html` | Declaration comparison | [Document][difference-javax-rmi-ssl-SslRMIClientSocketFactory] |
| `javax/rmi/ssl/SslRMIServerSocketFactory.html` | Declaration comparison | [Document][difference-javax-rmi-ssl-SslRMIServerSocketFactory] |
| `javax/rmi/ssl/package-summary.html` | Package comparison | [Document][difference-javax-rmi-ssl-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-java-rmi-AccessException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/AccessException.html
[difference-java-rmi-AlreadyBoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/AlreadyBoundException.html
[difference-java-rmi-ConnectException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/ConnectException.html
[difference-java-rmi-ConnectIOException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/ConnectIOException.html
[difference-java-rmi-MarshalException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/MarshalException.html
[difference-java-rmi-MarshalledObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/MarshalledObject.html
[difference-java-rmi-Naming]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/Naming.html
[difference-java-rmi-NoSuchObjectException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/NoSuchObjectException.html
[difference-java-rmi-NotBoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/NotBoundException.html
[difference-java-rmi-RMISecurityException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/RMISecurityException.html
[difference-java-rmi-RMISecurityManager]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/RMISecurityManager.html
[difference-java-rmi-Remote]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/Remote.html
[difference-java-rmi-RemoteException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/RemoteException.html
[difference-java-rmi-ServerError]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/ServerError.html
[difference-java-rmi-ServerException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/ServerException.html
[difference-java-rmi-ServerRuntimeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/ServerRuntimeException.html
[difference-java-rmi-StubNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/StubNotFoundException.html
[difference-java-rmi-UnexpectedException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/UnexpectedException.html
[difference-java-rmi-UnknownHostException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/UnknownHostException.html
[difference-java-rmi-UnmarshalException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/UnmarshalException.html
[difference-java-rmi-dgc-DGC]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/dgc/DGC.html
[difference-java-rmi-dgc-Lease]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/dgc/Lease.html
[difference-java-rmi-dgc-VMID]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/dgc/VMID.html
[difference-java-rmi-dgc-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/dgc/package-summary.html
[difference-java-rmi-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/package-summary.html
[difference-java-rmi-registry-LocateRegistry]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/registry/LocateRegistry.html
[difference-java-rmi-registry-Registry]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/registry/Registry.html
[difference-java-rmi-registry-RegistryHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/registry/RegistryHandler.html
[difference-java-rmi-registry-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/registry/package-summary.html
[difference-java-rmi-server-ExportException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/ExportException.html
[difference-java-rmi-server-LoaderHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/LoaderHandler.html
[difference-java-rmi-server-LogStream]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/LogStream.html
[difference-java-rmi-server-ObjID]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/ObjID.html
[difference-java-rmi-server-Operation]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/Operation.html
[difference-java-rmi-server-RMIClassLoader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMIClassLoader.html
[difference-java-rmi-server-RMIClassLoaderSpi]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMIClassLoaderSpi.html
[difference-java-rmi-server-RMIClientSocketFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMIClientSocketFactory.html
[difference-java-rmi-server-RMIFailureHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMIFailureHandler.html
[difference-java-rmi-server-RMIServerSocketFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMIServerSocketFactory.html
[difference-java-rmi-server-RMISocketFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RMISocketFactory.html
[difference-java-rmi-server-RemoteCall]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteCall.html
[difference-java-rmi-server-RemoteObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteObject.html
[difference-java-rmi-server-RemoteObjectInvocationHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteObjectInvocationHandler.html
[difference-java-rmi-server-RemoteRef]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteRef.html
[difference-java-rmi-server-RemoteServer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteServer.html
[difference-java-rmi-server-RemoteStub]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/RemoteStub.html
[difference-java-rmi-server-ServerCloneException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/ServerCloneException.html
[difference-java-rmi-server-ServerNotActiveException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/ServerNotActiveException.html
[difference-java-rmi-server-ServerRef]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/ServerRef.html
[difference-java-rmi-server-Skeleton]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/Skeleton.html
[difference-java-rmi-server-SkeletonMismatchException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/SkeletonMismatchException.html
[difference-java-rmi-server-SkeletonNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/SkeletonNotFoundException.html
[difference-java-rmi-server-SocketSecurityException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/SocketSecurityException.html
[difference-java-rmi-server-UID]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/UID.html
[difference-java-rmi-server-UnicastRemoteObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/UnicastRemoteObject.html
[difference-java-rmi-server-Unreferenced]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/Unreferenced.html
[difference-java-rmi-server-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/java/rmi/server/package-summary.html
[difference-javax-rmi-ssl-SslRMIClientSocketFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/javax/rmi/ssl/SslRMIClientSocketFactory.html
[difference-javax-rmi-ssl-SslRMIServerSocketFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/javax/rmi/ssl/SslRMIServerSocketFactory.html
[difference-javax-rmi-ssl-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/javax/rmi/ssl/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.rmi/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
