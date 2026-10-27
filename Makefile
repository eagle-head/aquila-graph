MAVEN ?= ./mvnw
.DEFAULT_GOAL := compile
MAVEN_FLAGS ?= -B --no-transfer-progress
SKIP_TESTS ?= -Dmaven.test.skip=true

unexport MAVEN MAVEN_FLAGS SKIP_TESTS

export AQUILA_BUILD_MAVEN := $(value MAVEN)
export AQUILA_BUILD_FLAGS := $(value MAVEN_FLAGS)
export AQUILA_BUILD_SKIP_FLAGS := $(value SKIP_TESTS)

.PHONY: compile test integration-test integration-test-neo4j check-process check-persistence check-lifecycle prepare-consumers coverage format format-check line-endings-check quality supply-chain-check clean help

compile test integration-test integration-test-neo4j check-process check-persistence check-lifecycle prepare-consumers coverage format format-check line-endings-check quality supply-chain-check clean:
	@java --enable-native-access=ALL-UNNAMED config/build/org/aquilagraph/build/BuildExecution.java --make "$@"

help:
	@echo 'prepare-consumers: package and prepare the independent query module without running tests.'
	@echo 'check-process, check-persistence, check-lifecycle: focused integrations; complete validation remains incomplete.'
	@echo 'integration-test-neo4j: all integrations; coverage: complete units; quality: static gates.'
