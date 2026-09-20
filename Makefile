MAVEN ?= ./mvnw
MAVEN_FLAGS ?= -B --no-transfer-progress
SKIP_TESTS ?= -Dmaven.test.skip=true

.PHONY: compile format format-check quality supply-chain-check

compile:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) compile

format:
	$(MAVEN) $(MAVEN_FLAGS) spotless:apply

format-check:
	$(MAVEN) $(MAVEN_FLAGS) spotless:check

quality:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) verify

supply-chain-check:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) -Psupply-chain verify
