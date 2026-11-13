# Thin wrappers: every recipe is one command, and everything works without `just`.

# Enable the commit hooks once per clone.
hooks:
    git config core.hooksPath .githooks

# The Scala module's suites; the integration suites start Postgres in Docker.
test:
    sbt test

# The device package: type check and its tests.
test-device:
    cd device && npm ci && npm run typecheck && npm test

fmt:
    sbt scalafmtAll scalafmtSbt

# The living features, the glossary and the spec that names them.
check:
    uvx --from "git+https://github.com/thinkmorestupidless/speckit-bdd@v0.2.0#subdirectory=checker" speckit-bdd check --root . --glossary GLOSSARY.md --features features --specs specs
