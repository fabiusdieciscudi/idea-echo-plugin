all:	build

build:
	./gradlew build

run:	build
	./gradlew runIde

plugin:
	./gradlew buildPlugin
