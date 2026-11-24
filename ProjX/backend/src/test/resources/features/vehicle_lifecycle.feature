Feature: Vehicle lifecycle and availability management
  As a fleet operator
  I want to manage vehicle operational states
  So that unavailable vehicles cannot be reserved

  @US02 @BR2
  Scenario: Move an available bicycle to maintenance
    Given an active bicycle in the AVAILABLE state
    When the operator changes its state to MAINTENANCE
    Then the bicycle state should be MAINTENANCE
    And the bicycle should not be available for reservations

  @US02 @BR2
  Scenario: Deactivate a bicycle
    Given an active bicycle in the AVAILABLE state
    When the operator deactivates the bicycle
    Then the bicycle should be administratively inactive
    And the bicycle should not be available for reservations

  @US02 @BR2
  Scenario: Activate a bicycle
    Given an inactive bicycle in the AVAILABLE state
    When the operator activates the bicycle
    Then the bicycle should be administratively active
    And the bicycle lifecycle state should remain AVAILABLE

  @US02
  Scenario: Cancel pending reservations when maintenance begins
    Given a bicycle with a pending reservation
    When the operator changes its state to MAINTENANCE
    Then the reservation should be CANCELLED
    And the bicycle should not be available for reservations

  @US02
  Scenario: Reject an invalid lifecycle transition
    Given a retired bicycle
    When the operator attempts to change its state to AVAILABLE
    Then the operation should be rejected
    And the bicycle should remain RETIRED

  @US02
  Scenario: Preserve ongoing sessions during deactivation
    Given a bicycle with an ongoing usage session
    When the operator deactivates the bicycle
    Then the bicycle should be administratively inactive
    And the ongoing usage session should remain IN_USE
