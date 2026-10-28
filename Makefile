MAVEN ?= ./mvnw
.DEFAULT_GOAL := compile
MAVEN_FLAGS ?= -B --no-transfer-progress
SKIP_TESTS ?= -Dmaven.test.skip=true

unexport MAVEN MAVEN_FLAGS SKIP_TESTS

export AQUILA_BUILD_MAVEN := $(value MAVEN)
export AQUILA_BUILD_FLAGS := $(value MAVEN_FLAGS)
export AQUILA_BUILD_SKIP_FLAGS := $(value SKIP_TESTS)

.PHONY: compile test integration-test integration-test-neo4j check-process check-persistence check-lifecycle prepare-consumers verify-all coverage format format-check line-endings-check quality supply-chain-check clean help

compile test integration-test integration-test-neo4j check-process check-persistence check-lifecycle prepare-consumers verify-all coverage format format-check line-endings-check quality supply-chain-check clean:
	@java --enable-native-access=ALL-UNNAMED config/build/org/aquilagraph/build/BuildExecution.java --make "$@"

help:
	@echo 'test: development tests; coverage: the same scope with unit coverage; quality: static gates without tests.'
	@echo 'Unit selectors require explicit *Test class names, optionally followed by #method.'
	@echo 'Real-environment integration commands are deferred until a new explicit user decision.'
	@echo 'verify-all: complete unit/integration/static validation; not a development acceptance command.'
	@echo 'prepare-consumers: package and prepare the independent query module without running tests.'
	@echo 'check-process, check-persistence, check-lifecycle: manual focused integrations.'
	@echo 'integration-test: manual process integrations; integration-test-neo4j: all manual integrations.'
