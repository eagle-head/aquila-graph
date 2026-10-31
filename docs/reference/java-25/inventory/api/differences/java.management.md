# java.management API differences

[Differences index](../differences.md)

This is a document inventory of the Java SE 25 platform Annex 2 comparison between
`jdk-24-ga` and `jdk-25+36`. Each document can contain old, new, removed, unchanged, or
changed material. Its existence does not make all displayed declarations current Java 25 APIs.
The final API inventory owns current declaration membership; this inventory owns the
annotated comparison source identities. All listed comparison documents are **Source mapped**.

| Document path within module | Source role | Official source |
| --- | --- | --- |
| `java/lang/management/BufferPoolMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-BufferPoolMXBean] |
| `java/lang/management/ClassLoadingMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-ClassLoadingMXBean] |
| `java/lang/management/CompilationMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-CompilationMXBean] |
| `java/lang/management/GarbageCollectorMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-GarbageCollectorMXBean] |
| `java/lang/management/LockInfo.html` | Declaration comparison | [Document][difference-java-lang-management-LockInfo] |
| `java/lang/management/ManagementFactory.html` | Declaration comparison | [Document][difference-java-lang-management-ManagementFactory] |
| `java/lang/management/ManagementPermission.html` | Declaration comparison | [Document][difference-java-lang-management-ManagementPermission] |
| `java/lang/management/MemoryMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryMXBean] |
| `java/lang/management/MemoryManagerMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryManagerMXBean] |
| `java/lang/management/MemoryNotificationInfo.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryNotificationInfo] |
| `java/lang/management/MemoryPoolMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryPoolMXBean] |
| `java/lang/management/MemoryType.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryType] |
| `java/lang/management/MemoryUsage.html` | Declaration comparison | [Document][difference-java-lang-management-MemoryUsage] |
| `java/lang/management/MonitorInfo.html` | Declaration comparison | [Document][difference-java-lang-management-MonitorInfo] |
| `java/lang/management/OperatingSystemMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-OperatingSystemMXBean] |
| `java/lang/management/PlatformLoggingMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-PlatformLoggingMXBean] |
| `java/lang/management/PlatformManagedObject.html` | Declaration comparison | [Document][difference-java-lang-management-PlatformManagedObject] |
| `java/lang/management/RuntimeMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-RuntimeMXBean] |
| `java/lang/management/ThreadInfo.html` | Declaration comparison | [Document][difference-java-lang-management-ThreadInfo] |
| `java/lang/management/ThreadMXBean.html` | Declaration comparison | [Document][difference-java-lang-management-ThreadMXBean] |
| `java/lang/management/package-summary.html` | Package comparison | [Document][difference-java-lang-management-package-summary] |
| `javax/management/Attribute.html` | Declaration comparison | [Document][difference-javax-management-Attribute] |
| `javax/management/AttributeChangeNotification.html` | Declaration comparison | [Document][difference-javax-management-AttributeChangeNotification] |
| `javax/management/AttributeChangeNotificationFilter.html` | Declaration comparison | [Document][difference-javax-management-AttributeChangeNotificationFilter] |
| `javax/management/AttributeList.html` | Declaration comparison | [Document][difference-javax-management-AttributeList] |
| `javax/management/AttributeNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-AttributeNotFoundException] |
| `javax/management/AttributeValueExp.html` | Declaration comparison | [Document][difference-javax-management-AttributeValueExp] |
| `javax/management/BadAttributeValueExpException.html` | Declaration comparison | [Document][difference-javax-management-BadAttributeValueExpException] |
| `javax/management/BadBinaryOpValueExpException.html` | Declaration comparison | [Document][difference-javax-management-BadBinaryOpValueExpException] |
| `javax/management/BadStringOperationException.html` | Declaration comparison | [Document][difference-javax-management-BadStringOperationException] |
| `javax/management/ConstructorParameters.html` | Declaration comparison | [Document][difference-javax-management-ConstructorParameters] |
| `javax/management/DefaultLoaderRepository.html` | Declaration comparison | [Document][difference-javax-management-DefaultLoaderRepository] |
| `javax/management/Descriptor.html` | Declaration comparison | [Document][difference-javax-management-Descriptor] |
| `javax/management/DescriptorAccess.html` | Declaration comparison | [Document][difference-javax-management-DescriptorAccess] |
| `javax/management/DescriptorKey.html` | Declaration comparison | [Document][difference-javax-management-DescriptorKey] |
| `javax/management/DescriptorRead.html` | Declaration comparison | [Document][difference-javax-management-DescriptorRead] |
| `javax/management/DynamicMBean.html` | Declaration comparison | [Document][difference-javax-management-DynamicMBean] |
| `javax/management/ImmutableDescriptor.html` | Declaration comparison | [Document][difference-javax-management-ImmutableDescriptor] |
| `javax/management/InstanceAlreadyExistsException.html` | Declaration comparison | [Document][difference-javax-management-InstanceAlreadyExistsException] |
| `javax/management/InstanceNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-InstanceNotFoundException] |
| `javax/management/IntrospectionException.html` | Declaration comparison | [Document][difference-javax-management-IntrospectionException] |
| `javax/management/InvalidApplicationException.html` | Declaration comparison | [Document][difference-javax-management-InvalidApplicationException] |
| `javax/management/InvalidAttributeValueException.html` | Declaration comparison | [Document][difference-javax-management-InvalidAttributeValueException] |
| `javax/management/JMException.html` | Declaration comparison | [Document][difference-javax-management-JMException] |
| `javax/management/JMRuntimeException.html` | Declaration comparison | [Document][difference-javax-management-JMRuntimeException] |
| `javax/management/JMX.html` | Declaration comparison | [Document][difference-javax-management-JMX] |
| `javax/management/ListenerNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-ListenerNotFoundException] |
| `javax/management/MBeanAttributeInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanAttributeInfo] |
| `javax/management/MBeanConstructorInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanConstructorInfo] |
| `javax/management/MBeanException.html` | Declaration comparison | [Document][difference-javax-management-MBeanException] |
| `javax/management/MBeanFeatureInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanFeatureInfo] |
| `javax/management/MBeanInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanInfo] |
| `javax/management/MBeanNotificationInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanNotificationInfo] |
| `javax/management/MBeanOperationInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanOperationInfo] |
| `javax/management/MBeanParameterInfo.html` | Declaration comparison | [Document][difference-javax-management-MBeanParameterInfo] |
| `javax/management/MBeanPermission.html` | Declaration comparison | [Document][difference-javax-management-MBeanPermission] |
| `javax/management/MBeanRegistration.html` | Declaration comparison | [Document][difference-javax-management-MBeanRegistration] |
| `javax/management/MBeanRegistrationException.html` | Declaration comparison | [Document][difference-javax-management-MBeanRegistrationException] |
| `javax/management/MBeanServer.html` | Declaration comparison | [Document][difference-javax-management-MBeanServer] |
| `javax/management/MBeanServerBuilder.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerBuilder] |
| `javax/management/MBeanServerConnection.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerConnection] |
| `javax/management/MBeanServerDelegate.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerDelegate] |
| `javax/management/MBeanServerDelegateMBean.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerDelegateMBean] |
| `javax/management/MBeanServerFactory.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerFactory] |
| `javax/management/MBeanServerInvocationHandler.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerInvocationHandler] |
| `javax/management/MBeanServerNotification.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerNotification] |
| `javax/management/MBeanServerPermission.html` | Declaration comparison | [Document][difference-javax-management-MBeanServerPermission] |
| `javax/management/MBeanTrustPermission.html` | Declaration comparison | [Document][difference-javax-management-MBeanTrustPermission] |
| `javax/management/MXBean.html` | Declaration comparison | [Document][difference-javax-management-MXBean] |
| `javax/management/MalformedObjectNameException.html` | Declaration comparison | [Document][difference-javax-management-MalformedObjectNameException] |
| `javax/management/NotCompliantMBeanException.html` | Declaration comparison | [Document][difference-javax-management-NotCompliantMBeanException] |
| `javax/management/Notification.html` | Declaration comparison | [Document][difference-javax-management-Notification] |
| `javax/management/NotificationBroadcaster.html` | Declaration comparison | [Document][difference-javax-management-NotificationBroadcaster] |
| `javax/management/NotificationBroadcasterSupport.html` | Declaration comparison | [Document][difference-javax-management-NotificationBroadcasterSupport] |
| `javax/management/NotificationEmitter.html` | Declaration comparison | [Document][difference-javax-management-NotificationEmitter] |
| `javax/management/NotificationFilter.html` | Declaration comparison | [Document][difference-javax-management-NotificationFilter] |
| `javax/management/NotificationFilterSupport.html` | Declaration comparison | [Document][difference-javax-management-NotificationFilterSupport] |
| `javax/management/NotificationListener.html` | Declaration comparison | [Document][difference-javax-management-NotificationListener] |
| `javax/management/ObjectInstance.html` | Declaration comparison | [Document][difference-javax-management-ObjectInstance] |
| `javax/management/ObjectName.html` | Declaration comparison | [Document][difference-javax-management-ObjectName] |
| `javax/management/OperationsException.html` | Declaration comparison | [Document][difference-javax-management-OperationsException] |
| `javax/management/PersistentMBean.html` | Declaration comparison | [Document][difference-javax-management-PersistentMBean] |
| `javax/management/Query.html` | Declaration comparison | [Document][difference-javax-management-Query] |
| `javax/management/QueryEval.html` | Declaration comparison | [Document][difference-javax-management-QueryEval] |
| `javax/management/QueryExp.html` | Declaration comparison | [Document][difference-javax-management-QueryExp] |
| `javax/management/ReflectionException.html` | Declaration comparison | [Document][difference-javax-management-ReflectionException] |
| `javax/management/RuntimeErrorException.html` | Declaration comparison | [Document][difference-javax-management-RuntimeErrorException] |
| `javax/management/RuntimeMBeanException.html` | Declaration comparison | [Document][difference-javax-management-RuntimeMBeanException] |
| `javax/management/RuntimeOperationsException.html` | Declaration comparison | [Document][difference-javax-management-RuntimeOperationsException] |
| `javax/management/ServiceNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-ServiceNotFoundException] |
| `javax/management/StandardEmitterMBean.html` | Declaration comparison | [Document][difference-javax-management-StandardEmitterMBean] |
| `javax/management/StandardMBean.html` | Declaration comparison | [Document][difference-javax-management-StandardMBean] |
| `javax/management/StringValueExp.html` | Declaration comparison | [Document][difference-javax-management-StringValueExp] |
| `javax/management/ValueExp.html` | Declaration comparison | [Document][difference-javax-management-ValueExp] |
| `javax/management/loading/ClassLoaderRepository.html` | Declaration comparison | [Document][difference-javax-management-loading-ClassLoaderRepository] |
| `javax/management/loading/DefaultLoaderRepository.html` | Declaration comparison | [Document][difference-javax-management-loading-DefaultLoaderRepository] |
| `javax/management/loading/PrivateClassLoader.html` | Declaration comparison | [Document][difference-javax-management-loading-PrivateClassLoader] |
| `javax/management/loading/package-summary.html` | Package comparison | [Document][difference-javax-management-loading-package-summary] |
| `javax/management/modelmbean/DescriptorSupport.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-DescriptorSupport] |
| `javax/management/modelmbean/InvalidTargetObjectTypeException.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-InvalidTargetObjectTypeException] |
| `javax/management/modelmbean/ModelMBean.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBean] |
| `javax/management/modelmbean/ModelMBeanAttributeInfo.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanAttributeInfo] |
| `javax/management/modelmbean/ModelMBeanConstructorInfo.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanConstructorInfo] |
| `javax/management/modelmbean/ModelMBeanInfo.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanInfo] |
| `javax/management/modelmbean/ModelMBeanInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanInfoSupport] |
| `javax/management/modelmbean/ModelMBeanNotificationBroadcaster.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanNotificationBroadcaster] |
| `javax/management/modelmbean/ModelMBeanNotificationInfo.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanNotificationInfo] |
| `javax/management/modelmbean/ModelMBeanOperationInfo.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-ModelMBeanOperationInfo] |
| `javax/management/modelmbean/RequiredModelMBean.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-RequiredModelMBean] |
| `javax/management/modelmbean/XMLParseException.html` | Declaration comparison | [Document][difference-javax-management-modelmbean-XMLParseException] |
| `javax/management/modelmbean/package-summary.html` | Package comparison | [Document][difference-javax-management-modelmbean-package-summary] |
| `javax/management/monitor/CounterMonitor.html` | Declaration comparison | [Document][difference-javax-management-monitor-CounterMonitor] |
| `javax/management/monitor/CounterMonitorMBean.html` | Declaration comparison | [Document][difference-javax-management-monitor-CounterMonitorMBean] |
| `javax/management/monitor/GaugeMonitor.html` | Declaration comparison | [Document][difference-javax-management-monitor-GaugeMonitor] |
| `javax/management/monitor/GaugeMonitorMBean.html` | Declaration comparison | [Document][difference-javax-management-monitor-GaugeMonitorMBean] |
| `javax/management/monitor/Monitor.html` | Declaration comparison | [Document][difference-javax-management-monitor-Monitor] |
| `javax/management/monitor/MonitorMBean.html` | Declaration comparison | [Document][difference-javax-management-monitor-MonitorMBean] |
| `javax/management/monitor/MonitorNotification.html` | Declaration comparison | [Document][difference-javax-management-monitor-MonitorNotification] |
| `javax/management/monitor/MonitorSettingException.html` | Declaration comparison | [Document][difference-javax-management-monitor-MonitorSettingException] |
| `javax/management/monitor/StringMonitor.html` | Declaration comparison | [Document][difference-javax-management-monitor-StringMonitor] |
| `javax/management/monitor/StringMonitorMBean.html` | Declaration comparison | [Document][difference-javax-management-monitor-StringMonitorMBean] |
| `javax/management/monitor/package-summary.html` | Package comparison | [Document][difference-javax-management-monitor-package-summary] |
| `javax/management/openmbean/ArrayType.html` | Declaration comparison | [Document][difference-javax-management-openmbean-ArrayType] |
| `javax/management/openmbean/CompositeData.html` | Declaration comparison | [Document][difference-javax-management-openmbean-CompositeData] |
| `javax/management/openmbean/CompositeDataInvocationHandler.html` | Declaration comparison | [Document][difference-javax-management-openmbean-CompositeDataInvocationHandler] |
| `javax/management/openmbean/CompositeDataSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-CompositeDataSupport] |
| `javax/management/openmbean/CompositeDataView.html` | Declaration comparison | [Document][difference-javax-management-openmbean-CompositeDataView] |
| `javax/management/openmbean/CompositeType.html` | Declaration comparison | [Document][difference-javax-management-openmbean-CompositeType] |
| `javax/management/openmbean/InvalidKeyException.html` | Declaration comparison | [Document][difference-javax-management-openmbean-InvalidKeyException] |
| `javax/management/openmbean/InvalidOpenTypeException.html` | Declaration comparison | [Document][difference-javax-management-openmbean-InvalidOpenTypeException] |
| `javax/management/openmbean/KeyAlreadyExistsException.html` | Declaration comparison | [Document][difference-javax-management-openmbean-KeyAlreadyExistsException] |
| `javax/management/openmbean/OpenDataException.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenDataException] |
| `javax/management/openmbean/OpenMBeanAttributeInfo.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanAttributeInfo] |
| `javax/management/openmbean/OpenMBeanAttributeInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanAttributeInfoSupport] |
| `javax/management/openmbean/OpenMBeanConstructorInfo.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanConstructorInfo] |
| `javax/management/openmbean/OpenMBeanConstructorInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanConstructorInfoSupport] |
| `javax/management/openmbean/OpenMBeanInfo.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanInfo] |
| `javax/management/openmbean/OpenMBeanInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanInfoSupport] |
| `javax/management/openmbean/OpenMBeanOperationInfo.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanOperationInfo] |
| `javax/management/openmbean/OpenMBeanOperationInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanOperationInfoSupport] |
| `javax/management/openmbean/OpenMBeanParameterInfo.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanParameterInfo] |
| `javax/management/openmbean/OpenMBeanParameterInfoSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenMBeanParameterInfoSupport] |
| `javax/management/openmbean/OpenType.html` | Declaration comparison | [Document][difference-javax-management-openmbean-OpenType] |
| `javax/management/openmbean/SimpleType.html` | Declaration comparison | [Document][difference-javax-management-openmbean-SimpleType] |
| `javax/management/openmbean/TabularData.html` | Declaration comparison | [Document][difference-javax-management-openmbean-TabularData] |
| `javax/management/openmbean/TabularDataSupport.html` | Declaration comparison | [Document][difference-javax-management-openmbean-TabularDataSupport] |
| `javax/management/openmbean/TabularType.html` | Declaration comparison | [Document][difference-javax-management-openmbean-TabularType] |
| `javax/management/openmbean/package-summary.html` | Package comparison | [Document][difference-javax-management-openmbean-package-summary] |
| `javax/management/package-summary.html` | Package comparison | [Document][difference-javax-management-package-summary] |
| `javax/management/relation/InvalidRelationIdException.html` | Declaration comparison | [Document][difference-javax-management-relation-InvalidRelationIdException] |
| `javax/management/relation/InvalidRelationServiceException.html` | Declaration comparison | [Document][difference-javax-management-relation-InvalidRelationServiceException] |
| `javax/management/relation/InvalidRelationTypeException.html` | Declaration comparison | [Document][difference-javax-management-relation-InvalidRelationTypeException] |
| `javax/management/relation/InvalidRoleInfoException.html` | Declaration comparison | [Document][difference-javax-management-relation-InvalidRoleInfoException] |
| `javax/management/relation/InvalidRoleValueException.html` | Declaration comparison | [Document][difference-javax-management-relation-InvalidRoleValueException] |
| `javax/management/relation/MBeanServerNotificationFilter.html` | Declaration comparison | [Document][difference-javax-management-relation-MBeanServerNotificationFilter] |
| `javax/management/relation/Relation.html` | Declaration comparison | [Document][difference-javax-management-relation-Relation] |
| `javax/management/relation/RelationException.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationException] |
| `javax/management/relation/RelationNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationNotFoundException] |
| `javax/management/relation/RelationNotification.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationNotification] |
| `javax/management/relation/RelationService.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationService] |
| `javax/management/relation/RelationServiceMBean.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationServiceMBean] |
| `javax/management/relation/RelationServiceNotRegisteredException.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationServiceNotRegisteredException] |
| `javax/management/relation/RelationSupport.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationSupport] |
| `javax/management/relation/RelationSupportMBean.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationSupportMBean] |
| `javax/management/relation/RelationType.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationType] |
| `javax/management/relation/RelationTypeNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationTypeNotFoundException] |
| `javax/management/relation/RelationTypeSupport.html` | Declaration comparison | [Document][difference-javax-management-relation-RelationTypeSupport] |
| `javax/management/relation/Role.html` | Declaration comparison | [Document][difference-javax-management-relation-Role] |
| `javax/management/relation/RoleInfo.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleInfo] |
| `javax/management/relation/RoleInfoNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleInfoNotFoundException] |
| `javax/management/relation/RoleList.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleList] |
| `javax/management/relation/RoleNotFoundException.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleNotFoundException] |
| `javax/management/relation/RoleResult.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleResult] |
| `javax/management/relation/RoleStatus.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleStatus] |
| `javax/management/relation/RoleUnresolved.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleUnresolved] |
| `javax/management/relation/RoleUnresolvedList.html` | Declaration comparison | [Document][difference-javax-management-relation-RoleUnresolvedList] |
| `javax/management/relation/package-summary.html` | Package comparison | [Document][difference-javax-management-relation-package-summary] |
| `javax/management/remote/JMXAddressable.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXAddressable] |
| `javax/management/remote/JMXAuthenticator.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXAuthenticator] |
| `javax/management/remote/JMXConnectionNotification.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectionNotification] |
| `javax/management/remote/JMXConnector.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnector] |
| `javax/management/remote/JMXConnectorFactory.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorFactory] |
| `javax/management/remote/JMXConnectorProvider.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorProvider] |
| `javax/management/remote/JMXConnectorServer.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorServer] |
| `javax/management/remote/JMXConnectorServerFactory.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorServerFactory] |
| `javax/management/remote/JMXConnectorServerMBean.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorServerMBean] |
| `javax/management/remote/JMXConnectorServerProvider.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXConnectorServerProvider] |
| `javax/management/remote/JMXPrincipal.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXPrincipal] |
| `javax/management/remote/JMXProviderException.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXProviderException] |
| `javax/management/remote/JMXServerErrorException.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXServerErrorException] |
| `javax/management/remote/JMXServiceURL.html` | Declaration comparison | [Document][difference-javax-management-remote-JMXServiceURL] |
| `javax/management/remote/MBeanServerForwarder.html` | Declaration comparison | [Document][difference-javax-management-remote-MBeanServerForwarder] |
| `javax/management/remote/NotificationResult.html` | Declaration comparison | [Document][difference-javax-management-remote-NotificationResult] |
| `javax/management/remote/SubjectDelegationPermission.html` | Declaration comparison | [Document][difference-javax-management-remote-SubjectDelegationPermission] |
| `javax/management/remote/TargetedNotification.html` | Declaration comparison | [Document][difference-javax-management-remote-TargetedNotification] |
| `javax/management/remote/package-summary.html` | Package comparison | [Document][difference-javax-management-remote-package-summary] |
| `javax/management/timer/Timer.html` | Declaration comparison | [Document][difference-javax-management-timer-Timer] |
| `javax/management/timer/TimerMBean.html` | Declaration comparison | [Document][difference-javax-management-timer-TimerMBean] |
| `javax/management/timer/TimerNotification.html` | Declaration comparison | [Document][difference-javax-management-timer-TimerNotification] |
| `javax/management/timer/package-summary.html` | Package comparison | [Document][difference-javax-management-timer-package-summary] |
| `module-summary.html` | Module comparison | [Document][difference-module-summary] |

## References

- [JSR 400 Annex 2][map-link-1].
- Every document row links to its exact source in that publication.

[difference-java-lang-management-BufferPoolMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/BufferPoolMXBean.html
[difference-java-lang-management-ClassLoadingMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/ClassLoadingMXBean.html
[difference-java-lang-management-CompilationMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/CompilationMXBean.html
[difference-java-lang-management-GarbageCollectorMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/GarbageCollectorMXBean.html
[difference-java-lang-management-LockInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/LockInfo.html
[difference-java-lang-management-ManagementFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/ManagementFactory.html
[difference-java-lang-management-ManagementPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/ManagementPermission.html
[difference-java-lang-management-MemoryMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryMXBean.html
[difference-java-lang-management-MemoryManagerMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryManagerMXBean.html
[difference-java-lang-management-MemoryNotificationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryNotificationInfo.html
[difference-java-lang-management-MemoryPoolMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryPoolMXBean.html
[difference-java-lang-management-MemoryType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryType.html
[difference-java-lang-management-MemoryUsage]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MemoryUsage.html
[difference-java-lang-management-MonitorInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/MonitorInfo.html
[difference-java-lang-management-OperatingSystemMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/OperatingSystemMXBean.html
[difference-java-lang-management-PlatformLoggingMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/PlatformLoggingMXBean.html
[difference-java-lang-management-PlatformManagedObject]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/PlatformManagedObject.html
[difference-java-lang-management-RuntimeMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/RuntimeMXBean.html
[difference-java-lang-management-ThreadInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/ThreadInfo.html
[difference-java-lang-management-ThreadMXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/ThreadMXBean.html
[difference-java-lang-management-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/java/lang/management/package-summary.html
[difference-javax-management-Attribute]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/Attribute.html
[difference-javax-management-AttributeChangeNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/AttributeChangeNotification.html
[difference-javax-management-AttributeChangeNotificationFilter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/AttributeChangeNotificationFilter.html
[difference-javax-management-AttributeList]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/AttributeList.html
[difference-javax-management-AttributeNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/AttributeNotFoundException.html
[difference-javax-management-AttributeValueExp]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/AttributeValueExp.html
[difference-javax-management-BadAttributeValueExpException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/BadAttributeValueExpException.html
[difference-javax-management-BadBinaryOpValueExpException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/BadBinaryOpValueExpException.html
[difference-javax-management-BadStringOperationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/BadStringOperationException.html
[difference-javax-management-ConstructorParameters]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ConstructorParameters.html
[difference-javax-management-DefaultLoaderRepository]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/DefaultLoaderRepository.html
[difference-javax-management-Descriptor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/Descriptor.html
[difference-javax-management-DescriptorAccess]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/DescriptorAccess.html
[difference-javax-management-DescriptorKey]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/DescriptorKey.html
[difference-javax-management-DescriptorRead]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/DescriptorRead.html
[difference-javax-management-DynamicMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/DynamicMBean.html
[difference-javax-management-ImmutableDescriptor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ImmutableDescriptor.html
[difference-javax-management-InstanceAlreadyExistsException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/InstanceAlreadyExistsException.html
[difference-javax-management-InstanceNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/InstanceNotFoundException.html
[difference-javax-management-IntrospectionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/IntrospectionException.html
[difference-javax-management-InvalidApplicationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/InvalidApplicationException.html
[difference-javax-management-InvalidAttributeValueException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/InvalidAttributeValueException.html
[difference-javax-management-JMException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/JMException.html
[difference-javax-management-JMRuntimeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/JMRuntimeException.html
[difference-javax-management-JMX]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/JMX.html
[difference-javax-management-ListenerNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ListenerNotFoundException.html
[difference-javax-management-MBeanAttributeInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanAttributeInfo.html
[difference-javax-management-MBeanConstructorInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanConstructorInfo.html
[difference-javax-management-MBeanException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanException.html
[difference-javax-management-MBeanFeatureInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanFeatureInfo.html
[difference-javax-management-MBeanInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanInfo.html
[difference-javax-management-MBeanNotificationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanNotificationInfo.html
[difference-javax-management-MBeanOperationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanOperationInfo.html
[difference-javax-management-MBeanParameterInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanParameterInfo.html
[difference-javax-management-MBeanPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanPermission.html
[difference-javax-management-MBeanRegistration]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanRegistration.html
[difference-javax-management-MBeanRegistrationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanRegistrationException.html
[difference-javax-management-MBeanServer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServer.html
[difference-javax-management-MBeanServerBuilder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerBuilder.html
[difference-javax-management-MBeanServerConnection]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerConnection.html
[difference-javax-management-MBeanServerDelegate]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerDelegate.html
[difference-javax-management-MBeanServerDelegateMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerDelegateMBean.html
[difference-javax-management-MBeanServerFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerFactory.html
[difference-javax-management-MBeanServerInvocationHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerInvocationHandler.html
[difference-javax-management-MBeanServerNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerNotification.html
[difference-javax-management-MBeanServerPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanServerPermission.html
[difference-javax-management-MBeanTrustPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MBeanTrustPermission.html
[difference-javax-management-MXBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MXBean.html
[difference-javax-management-MalformedObjectNameException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/MalformedObjectNameException.html
[difference-javax-management-NotCompliantMBeanException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotCompliantMBeanException.html
[difference-javax-management-Notification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/Notification.html
[difference-javax-management-NotificationBroadcaster]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationBroadcaster.html
[difference-javax-management-NotificationBroadcasterSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationBroadcasterSupport.html
[difference-javax-management-NotificationEmitter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationEmitter.html
[difference-javax-management-NotificationFilter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationFilter.html
[difference-javax-management-NotificationFilterSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationFilterSupport.html
[difference-javax-management-NotificationListener]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/NotificationListener.html
[difference-javax-management-ObjectInstance]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ObjectInstance.html
[difference-javax-management-ObjectName]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ObjectName.html
[difference-javax-management-OperationsException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/OperationsException.html
[difference-javax-management-PersistentMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/PersistentMBean.html
[difference-javax-management-Query]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/Query.html
[difference-javax-management-QueryEval]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/QueryEval.html
[difference-javax-management-QueryExp]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/QueryExp.html
[difference-javax-management-ReflectionException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ReflectionException.html
[difference-javax-management-RuntimeErrorException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/RuntimeErrorException.html
[difference-javax-management-RuntimeMBeanException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/RuntimeMBeanException.html
[difference-javax-management-RuntimeOperationsException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/RuntimeOperationsException.html
[difference-javax-management-ServiceNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ServiceNotFoundException.html
[difference-javax-management-StandardEmitterMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/StandardEmitterMBean.html
[difference-javax-management-StandardMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/StandardMBean.html
[difference-javax-management-StringValueExp]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/StringValueExp.html
[difference-javax-management-ValueExp]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/ValueExp.html
[difference-javax-management-loading-ClassLoaderRepository]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/loading/ClassLoaderRepository.html
[difference-javax-management-loading-DefaultLoaderRepository]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/loading/DefaultLoaderRepository.html
[difference-javax-management-loading-PrivateClassLoader]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/loading/PrivateClassLoader.html
[difference-javax-management-loading-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/loading/package-summary.html
[difference-javax-management-modelmbean-DescriptorSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/DescriptorSupport.html
[difference-javax-management-modelmbean-InvalidTargetObjectTypeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/InvalidTargetObjectTypeException.html
[difference-javax-management-modelmbean-ModelMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBean.html
[difference-javax-management-modelmbean-ModelMBeanAttributeInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanAttributeInfo.html
[difference-javax-management-modelmbean-ModelMBeanConstructorInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanConstructorInfo.html
[difference-javax-management-modelmbean-ModelMBeanInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanInfo.html
[difference-javax-management-modelmbean-ModelMBeanInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanInfoSupport.html
[difference-javax-management-modelmbean-ModelMBeanNotificationBroadcaster]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanNotificationBroadcaster.html
[difference-javax-management-modelmbean-ModelMBeanNotificationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanNotificationInfo.html
[difference-javax-management-modelmbean-ModelMBeanOperationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/ModelMBeanOperationInfo.html
[difference-javax-management-modelmbean-RequiredModelMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/RequiredModelMBean.html
[difference-javax-management-modelmbean-XMLParseException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/XMLParseException.html
[difference-javax-management-modelmbean-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/modelmbean/package-summary.html
[difference-javax-management-monitor-CounterMonitor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/CounterMonitor.html
[difference-javax-management-monitor-CounterMonitorMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/CounterMonitorMBean.html
[difference-javax-management-monitor-GaugeMonitor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/GaugeMonitor.html
[difference-javax-management-monitor-GaugeMonitorMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/GaugeMonitorMBean.html
[difference-javax-management-monitor-Monitor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/Monitor.html
[difference-javax-management-monitor-MonitorMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/MonitorMBean.html
[difference-javax-management-monitor-MonitorNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/MonitorNotification.html
[difference-javax-management-monitor-MonitorSettingException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/MonitorSettingException.html
[difference-javax-management-monitor-StringMonitor]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/StringMonitor.html
[difference-javax-management-monitor-StringMonitorMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/StringMonitorMBean.html
[difference-javax-management-monitor-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/monitor/package-summary.html
[difference-javax-management-openmbean-ArrayType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/ArrayType.html
[difference-javax-management-openmbean-CompositeData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/CompositeData.html
[difference-javax-management-openmbean-CompositeDataInvocationHandler]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/CompositeDataInvocationHandler.html
[difference-javax-management-openmbean-CompositeDataSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/CompositeDataSupport.html
[difference-javax-management-openmbean-CompositeDataView]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/CompositeDataView.html
[difference-javax-management-openmbean-CompositeType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/CompositeType.html
[difference-javax-management-openmbean-InvalidKeyException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/InvalidKeyException.html
[difference-javax-management-openmbean-InvalidOpenTypeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/InvalidOpenTypeException.html
[difference-javax-management-openmbean-KeyAlreadyExistsException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/KeyAlreadyExistsException.html
[difference-javax-management-openmbean-OpenDataException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenDataException.html
[difference-javax-management-openmbean-OpenMBeanAttributeInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanAttributeInfo.html
[difference-javax-management-openmbean-OpenMBeanAttributeInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanAttributeInfoSupport.html
[difference-javax-management-openmbean-OpenMBeanConstructorInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanConstructorInfo.html
[difference-javax-management-openmbean-OpenMBeanConstructorInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanConstructorInfoSupport.html
[difference-javax-management-openmbean-OpenMBeanInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanInfo.html
[difference-javax-management-openmbean-OpenMBeanInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanInfoSupport.html
[difference-javax-management-openmbean-OpenMBeanOperationInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanOperationInfo.html
[difference-javax-management-openmbean-OpenMBeanOperationInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanOperationInfoSupport.html
[difference-javax-management-openmbean-OpenMBeanParameterInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanParameterInfo.html
[difference-javax-management-openmbean-OpenMBeanParameterInfoSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenMBeanParameterInfoSupport.html
[difference-javax-management-openmbean-OpenType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/OpenType.html
[difference-javax-management-openmbean-SimpleType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/SimpleType.html
[difference-javax-management-openmbean-TabularData]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/TabularData.html
[difference-javax-management-openmbean-TabularDataSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/TabularDataSupport.html
[difference-javax-management-openmbean-TabularType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/TabularType.html
[difference-javax-management-openmbean-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/openmbean/package-summary.html
[difference-javax-management-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/package-summary.html
[difference-javax-management-relation-InvalidRelationIdException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/InvalidRelationIdException.html
[difference-javax-management-relation-InvalidRelationServiceException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/InvalidRelationServiceException.html
[difference-javax-management-relation-InvalidRelationTypeException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/InvalidRelationTypeException.html
[difference-javax-management-relation-InvalidRoleInfoException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/InvalidRoleInfoException.html
[difference-javax-management-relation-InvalidRoleValueException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/InvalidRoleValueException.html
[difference-javax-management-relation-MBeanServerNotificationFilter]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/MBeanServerNotificationFilter.html
[difference-javax-management-relation-Relation]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/Relation.html
[difference-javax-management-relation-RelationException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationException.html
[difference-javax-management-relation-RelationNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationNotFoundException.html
[difference-javax-management-relation-RelationNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationNotification.html
[difference-javax-management-relation-RelationService]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationService.html
[difference-javax-management-relation-RelationServiceMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationServiceMBean.html
[difference-javax-management-relation-RelationServiceNotRegisteredException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationServiceNotRegisteredException.html
[difference-javax-management-relation-RelationSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationSupport.html
[difference-javax-management-relation-RelationSupportMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationSupportMBean.html
[difference-javax-management-relation-RelationType]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationType.html
[difference-javax-management-relation-RelationTypeNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationTypeNotFoundException.html
[difference-javax-management-relation-RelationTypeSupport]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RelationTypeSupport.html
[difference-javax-management-relation-Role]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/Role.html
[difference-javax-management-relation-RoleInfo]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleInfo.html
[difference-javax-management-relation-RoleInfoNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleInfoNotFoundException.html
[difference-javax-management-relation-RoleList]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleList.html
[difference-javax-management-relation-RoleNotFoundException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleNotFoundException.html
[difference-javax-management-relation-RoleResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleResult.html
[difference-javax-management-relation-RoleStatus]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleStatus.html
[difference-javax-management-relation-RoleUnresolved]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleUnresolved.html
[difference-javax-management-relation-RoleUnresolvedList]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/RoleUnresolvedList.html
[difference-javax-management-relation-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/relation/package-summary.html
[difference-javax-management-remote-JMXAddressable]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXAddressable.html
[difference-javax-management-remote-JMXAuthenticator]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXAuthenticator.html
[difference-javax-management-remote-JMXConnectionNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectionNotification.html
[difference-javax-management-remote-JMXConnector]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnector.html
[difference-javax-management-remote-JMXConnectorFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorFactory.html
[difference-javax-management-remote-JMXConnectorProvider]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorProvider.html
[difference-javax-management-remote-JMXConnectorServer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorServer.html
[difference-javax-management-remote-JMXConnectorServerFactory]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorServerFactory.html
[difference-javax-management-remote-JMXConnectorServerMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorServerMBean.html
[difference-javax-management-remote-JMXConnectorServerProvider]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXConnectorServerProvider.html
[difference-javax-management-remote-JMXPrincipal]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXPrincipal.html
[difference-javax-management-remote-JMXProviderException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXProviderException.html
[difference-javax-management-remote-JMXServerErrorException]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXServerErrorException.html
[difference-javax-management-remote-JMXServiceURL]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/JMXServiceURL.html
[difference-javax-management-remote-MBeanServerForwarder]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/MBeanServerForwarder.html
[difference-javax-management-remote-NotificationResult]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/NotificationResult.html
[difference-javax-management-remote-SubjectDelegationPermission]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/SubjectDelegationPermission.html
[difference-javax-management-remote-TargetedNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/TargetedNotification.html
[difference-javax-management-remote-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/remote/package-summary.html
[difference-javax-management-timer-Timer]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/timer/Timer.html
[difference-javax-management-timer-TimerMBean]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/timer/TimerMBean.html
[difference-javax-management-timer-TimerNotification]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/timer/TimerNotification.html
[difference-javax-management-timer-package-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/javax/management/timer/package-summary.html
[difference-module-summary]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/java.management/module-summary.html

[map-link-1]: https://cr.openjdk.org/~iris/se/25/latestSpec/apidiffs/index.html
