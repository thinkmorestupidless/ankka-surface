Feature: A surface is held on the server and steered from any device
  The representation of what a person last asked for lives on the server, so that every device
  renders the same thing and any device can change it.

  Background:
    Given a vocabulary with the shapes "home, shelf, item" and the home shape "home"
    And the vocabulary names the anchor kinds "theme, market", the modifier keys "time" and the pending kinds "forecast"
    And the vocabulary names the focus keys "card, outcome"

  Scenario: Opening a surface gives it an owner and the home shape
    When a surface "s-1" is opened for "trevor"
    Then the surface "s-1" has the owner "trevor", the shape "home", no anchor and version 1

  Scenario: A move changes several parts at once and raises the version by one
    Given a surface "s-1" opened for "trevor"
    When "s-1" is moved to the shape "shelf" with the anchor "theme:newcastle" and the modifier "time=this weekend"
    Then the surface "s-1" has the shape "shelf", the anchor "theme:newcastle", the modifier "time=this weekend" and version 2

  Scenario: A move naming a shape the vocabulary lacks is refused and changes nothing
    Given a surface "s-1" opened for "trevor"
    When "s-1" is moved to the shape "basket"
    Then the move is refused as a bad request naming "basket"
    And the surface "s-1" has the shape "home" and version 1

  Scenario: A move on a surface nobody opened is refused as a conflict
    When "s-9" is moved to the shape "shelf"
    Then the move is refused as a conflict

  Scenario: A request is kept on the rail with the surface it left
    Given a surface "s-1" opened for "trevor"
    When "s-1" is moved to the shape "shelf" with the anchor "theme:newcastle" and the request "anything about Newcastle"
    And "s-1" is moved to the shape "item" with the anchor "market:nufc-ars" and the request "tell me about the derby"
    Then the rail of "s-1" reads, newest first, "tell me about the derby" at "item" and "anything about Newcastle" at "shelf"

  Scenario: The rail keeps only its length
    Given a surface "s-1" opened for "trevor" with a rail length of 2
    When three requests are made on "s-1"
    Then the rail of "s-1" holds the last two

  Scenario: A pending act is held and cleared
    Given a surface "s-1" opened for "trevor"
    When "s-1" is moved with the pending "forecast" of "outcome=Yes, estimate=70"
    Then the surface "s-1" holds the pending "forecast" with "estimate" "70"
    When "s-1" is moved clearing the pending
    Then the surface "s-1" holds no pending

  Scenario: A device shows and hides the surface
    Given a surface "s-1" opened for "trevor"
    When the device "tv-lounge" of class "tv" in a room shows "s-1"
    And the device "phone-1" of class "phone" shows "s-1"
    Then the surface "s-1" is showing on "tv-lounge" in a room and on "phone-1" not in a room
    When the device "tv-lounge" hides "s-1"
    Then the surface "s-1" is showing on "phone-1" only

  Scenario: Every version is rebuilt from the journal
    Given a surface "s-1" opened for "trevor" and moved twice
    When the service restarts
    Then the surface "s-1" reads as it did before the restart

  Scenario: What was said is interpreted and put on the rail
    Given a surface "s-1" opened for "trevor"
    And an interpreter that turns "newcastle" into a move to the shape "shelf" with the anchor "theme:newcastle"
    When "anything about Newcastle" is said on "s-1"
    Then the answer is understood and the surface "s-1" has the shape "shelf" and the request "anything about Newcastle" on its rail

  Scenario: What was not understood changes nothing and says why
    Given a surface "s-1" opened for "trevor"
    And an interpreter that understands nothing
    When "make me a sandwich" is said on "s-1"
    Then the answer is not understood with a reason, and the surface "s-1" is at version 1

  Scenario: A device streams every version as it is written
    Given a surface "s-1" opened for "trevor"
    And a device streaming "s-1"
    When "s-1" is moved to the shape "shelf"
    Then the device is given the surface at version 2 with the shape "shelf"

  Scenario: Closing a surface frees its id
    Given a surface "s-1" opened for "trevor"
    When "s-1" is closed
    Then reading "s-1" is not found
    And a device streaming "s-1" is told it was removed

  Scenario: The words of one utterance share one rail entry
    Given a surface "s-1" opened for "trevor"
    When "newcastle" is said on "s-1" from the utterance "u-1"
    And "newcastle please" is said on "s-1" from the utterance "u-1"
    And "newcastle again" is said on "s-1" from the utterance "u-2"
    Then the rail of "s-1" reads, newest first, "newcastle again" and "newcastle please"

  Scenario: Words are heard without anything being kept
    Given a surface "s-1" opened for "trevor"
    When "newcastle" is heard on "s-1"
    Then the answer is understood with a move to the anchor "theme:newcastle", and the surface "s-1" is at version 1

  Scenario: The focus beside a surface is the latest value and is streamed
    Given a surface "s-1" opened for "trevor"
    And a device streaming the focus of "s-1"
    When the device "phone-1" sets the focus of "s-1" to "card=m1"
    Then the focus of "s-1" is "card=m1" from "phone-1" at version 1
    And the device streaming the focus is given "card=m1"
    When the device "phone-1" sets the focus of "s-1" to "colour=red"
    Then the focus is refused as a bad request naming "colour"
