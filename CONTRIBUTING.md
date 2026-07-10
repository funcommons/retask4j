# Contributing to retask4j

Thank you for your interest in contributing! This document covers the basics.

## Development Setup

1. **Prerequisites**: Java 17+, Maven 3.6+, Redis (for integration tests)
2. **Clone**: `git clone https://github.com/your-org/retask4j.git`
3. **Build**: `mvn clean package -DskipTests`
4. **Run tests**:
   - Unit tests: `mvn test -pl retask4j-core,retask4j-http`
   - End-to-end tests (needs Redis): `mvn test -pl retask4j-core -Dtest=EndToEnd* -Dredis.host=localhost`

## Project Structure

- `retask4j-core/` — pure Redisson-based queue engine (no Spring)
- `retask4j-http/` — HTTP proxy layer (caller + worker sides)
- `retask4j-http-server/` — runnable demo server
- `retask4j-demo-taskcaller/` / `retask4j-demo-taskworker/` — standalone demos
- `documents/` — all documentation

## Coding Conventions

- Java 17 features are encouraged (records, sealed types, pattern matching where useful)
- Use Lombok (`@Getter`, `@Setter`, `@Slf4j`) to reduce boilerplate
- Tests use JUnit 5 + Mockito; integration tests use real Redis via Redisson
- Comments in English
- Public API: validate inputs in setters, throw `IllegalArgumentException` with descriptive messages
- Redis key names must be safe (no `:`, `{`, `}`, control chars); use `FuTaskMessage` setters which enforce this

## Pull Request Process

1. **Branch from `main`**: `git checkout -b feature/your-feature`
2. **Write tests** for any new behavior (unit tests at minimum)
3. **Run all tests** before pushing
4. **Update docs** in `documents/` if you change public API
5. **Keep PRs focused** — one feature/fix per PR
6. **Write a clear PR description** explaining the why, not just the what

## Reporting Issues

When reporting a bug, please include:
- retask4j version (commit hash or release tag)
- Java version (`java -version`)
- Redisson version (from your pom)
- Minimal reproduction (config + code)
- Expected vs actual behavior
- Stack trace if applicable

## Areas Where Help Is Welcome

- Bug reports and fixes
- Documentation improvements
- Performance benchmarks
- Additional language bindings (Python, Go, Node, etc.)
- More end-to-end integration tests
- Web dashboard enhancements
