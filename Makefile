# Convenience wrapper around Maven for Gitlet.
# All real work is done by Maven (pom.xml); this file just spells out
# the common commands so you don't have to remember them.

MVN    ?= ./mvnw
ifeq ($(shell test -x ./mvnw && echo yes),yes)
MVN     = ./mvnw
else
MVN     = mvn
endif

TESTER = python3 testing/tester.py --src $(CURDIR)/testing/src --progdir $(CURDIR)/target/classes
TESTS  = testing/samples/*.in testing/student_tests/*.in

.PHONY: all build jar clean check test run

all: build

# Compile sources and produce the runnable fat jar: target/gitlet.jar
build:
	$(MVN) -q package

jar: build

clean:
	$(MVN) -q clean

# Compile, then run the full integration suite (python3 required).
check: build
	$(TESTER) $(TESTS)

test: check

# Example usage:  make run ARGS="init"
run: build
	java -jar target/gitlet.jar $(ARGS)
