package com.thinkmorestupidless.ankka.surface

class VocabularySuite extends munit.FunSuite:

  test("a home shape that is not a shape is refused at construction") {
    intercept[IllegalArgumentException](Vocabulary(Set("shelf"), "home"))
  }

  test("refuse names the first word a move uses that the vocabulary lacks") {
    val v = Fixtures.vocabulary
    assertEquals(v.refuse(Move(shape = Some("shelf"))), None)
    assertEquals(v.refuse(Move(shape = Some("basket"))), Some("shape 'basket'"))
    assertEquals(v.refuse(Move(anchor = Some(Anchor("person", "p")))), Some("anchor kind 'person'"))
    assertEquals(
      v.refuse(Move(modifiers = Map("time" -> "x", "order" -> "y"))),
      Some("modifier key 'order'")
    )
    assertEquals(
      v.refuse(Move(pending = Some(Pending("stake", Map.empty)))),
      Some("pending kind 'stake'")
    )
  }
