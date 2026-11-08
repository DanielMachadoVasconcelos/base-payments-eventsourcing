# ADR-0004 — One Compose definition and executable BDD

Status: Accepted

## Context

Development and integration tests should exercise the same local dependencies. A readable scenario is useful only if the claimed behavior has executable evidence.

## Decision

Manage external dependencies through the root compose.yaml; do not use Testcontainers. Gradle integrationTest starts DynamoDB through Compose, while each suite owns isolated tables.

Express behavior through Given/When/Then JUnit tests. Keep pure aggregate tests separate from persistence and HTTP tests. Gherkin prose may explain a scenario but does not imply a Cucumber engine.

## Consequences and alternatives

The dependency stack has one owner and remains inspectable outside the test runner. Docker/Compose must be available, and tests must clean only their own tables.

Simulator after-effect failures demonstrate durable simulated effects, not actual payments-app crash recovery. Real process restart/multiple-instance tests are still required before claiming those guarantees. DynamoDB Local cannot prove AWS performance.

Scope: REQ-ENG-001, verified REQ-SIM requirements and planned REQ-TEST-001 in the [catalogue](../requirements.md).
