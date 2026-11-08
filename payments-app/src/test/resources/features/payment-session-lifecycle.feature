Feature: Lifecycle of a payment session

  Scenario: Preserve purchase history while the current purchase changes
    Given a merchant created a payment session for BRL 100.00
    When the merchant replaces the purchase with BRL 150.00 using the current ETag
    Then the current session version is incremented
    And the event stream retains the original purchase event
    And replaying the event stream produces the BRL 150.00 state

  Scenario: Store a purchase larger than a DynamoDB item
    Given a merchant submits a purchase whose attributes exceed 400 KiB
    When the payment session is admitted
    Then the purchase document is split into ordered DynamoDB chunks
    And events and current-state projections retain only the payload reference
    And a repeated Idempotency-Key returns the complete original response
