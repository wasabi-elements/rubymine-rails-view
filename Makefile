RUBYMINE_HOME ?= /Users/oliver/Applications/RubyMine.app

export RUBYMINE_HOME

.PHONY: build install run clean

build:
	./gradlew buildPlugin

# Build then open the distributions folder so you can drag the ZIP into RubyMine
install: build
	open build/distributions/

# Launch a sandboxed RubyMine with the plugin loaded (Mac only, needs a display)
run:
	./gradlew runIde

clean:
	./gradlew clean
