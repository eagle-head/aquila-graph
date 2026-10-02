MAVEN ?= ./mvnw
MAVEN_FLAGS ?= -B --no-transfer-progress
SKIP_TESTS ?= -Dmaven.test.skip=true

.PHONY: compile test integration-test integration-test-neo4j coverage format format-check line-endings-check quality supply-chain-check

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

line-endings-check:
	@cr="$$(printf '\r')"; \
	matches="$$(git grep --cached -I -l "$$cr")"; status=$$?; \
	if [ "$$status" -eq 0 ]; then printf 'Carriage returns in staged text:\n%s\n' "$$matches"; exit 1; fi; \
	if [ "$$status" -ne 1 ]; then exit "$$status"; fi; \
	commits="$$(git rev-list HEAD)" || exit $$?; \
	for commit in $$commits; do \
		matches="$$(git grep -I -l "$$cr" "$$commit")"; status=$$?; \
		if [ "$$status" -eq 0 ]; then printf 'Non-LF text in commit %s:\n%s\n' "$$commit" "$$matches"; exit 1; fi; \
		if [ "$$status" -ne 1 ]; then exit "$$status"; fi; \
	done; \
	printf 'LF-only Git text verified in the index and reachable commits.\n'

quality: line-endings-check
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) verify

supply-chain-check:
	$(MAVEN) $(MAVEN_FLAGS) $(SKIP_TESTS) -Psupply-chain verify
