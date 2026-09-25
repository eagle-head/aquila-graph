MAVEN ?= ./mvnw
MAVEN_FLAGS ?= -B --no-transfer-progress
SKIP_TESTS ?= -Dmaven.test.skip=true

.PHONY: compile test integration-test integration-test-neo4j coverage format format-check quality supply-chain-check

compile:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) compile

test:
	$(MAVEN) $(MAVEN_FLAGS) test

integration-test:
	$(MAVEN) $(MAVEN_FLAGS) -DskipTests=true verify

integration-test-neo4j:
	$(MAVEN) $(MAVEN_FLAGS) -DskipTests=true -Pneo4j-integration verify

coverage:
	$(MAVEN) $(MAVEN_FLAGS) jacoco:prepare-agent test jacoco:report

format:
	$(MAVEN) $(MAVEN_FLAGS) spotless:apply

format-check:
	$(MAVEN) $(MAVEN_FLAGS) spotless:check

quality:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) verify

supply-chain-check:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) -Psupply-chain verify
