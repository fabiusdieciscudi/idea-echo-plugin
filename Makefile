#
# © Copyright 2026-present by Fabius Dieciscudi. Licensed under the MIT License, see LICENSE.
#
#

.PHONY: all build test run plugin verify clean

all:	build

build:
	./gradlew build

test:
	./gradlew test

run:	build
	./gradlew runIde

plugin:
	./gradlew buildPlugin

verify:
	./gradlew verifyPlugin

# Uses rm rather than `gradlew clean`, so that it works when the build is
# broken -- which is when it is wanted.
clean:
	rm -rf build .gradle .intellijPlatform out
