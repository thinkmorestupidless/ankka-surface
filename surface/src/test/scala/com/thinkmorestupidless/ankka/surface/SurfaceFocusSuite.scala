package com.thinkmorestupidless.ankka.surface

import com.thinkmorestupidless.ankka.core.ErrorCode
import com.thinkmorestupidless.ankka.testkit.{KeyValueEntityTestKit, ViewTestKit}
import Fixtures.*

/** The focus lane with no runtime: the latest value, its version, and what the rows hold. */
class SurfaceFocusSuite extends munit.FunSuite:

  test("a focus set replaces the value whole and raises its version") {
    val kit   = KeyValueEntityTestKit.of(Focuses, "s-1")
    val first = kit.call(Focuses.set)(SetFocus("phone-1", Map("card" -> "m1"), t(1)))
    assertEquals(first.replyValue, Focus("s-1", "phone-1", Map("card" -> "m1"), t(1), 1L))
    val second = kit.call(Focuses.set)(SetFocus("phone-1", Map("outcome" -> "Yes"), t(2)))
    assertEquals(second.replyValue.fields, Map("outcome" -> "Yes"))
    assertEquals(second.replyValue.version, 2L)
    val cleared = kit.call(Focuses.set)(SetFocus("phone-1", Map.empty, t(3)))
    assertEquals(cleared.replyValue.fields, Map.empty)
    assertEquals(cleared.replyValue.version, 3L)
  }

  test("a focus key the vocabulary lacks is refused and changes nothing") {
    val kit    = KeyValueEntityTestKit.of(Focuses, "s-1")
    val result = kit.call(Focuses.set)(SetFocus("phone-1", Map("colour" -> "red"), t(1)))
    assertEquals(result.error.code, ErrorCode.BadRequest)
    assert(result.errorMessage.contains("colour"), result.errorMessage)
    assert(!result.changed)
  }

  test("a focus never set reads empty at version 0") {
    val kit = KeyValueEntityTestKit.of(Focuses, "s-2")
    assertEquals(kit.call(Focuses.get).replyValue, Focus.empty("s-2"))
  }

  test("the row is the latest focus") {
    val kit = ViewTestKit(FocusRows)
    kit.change("s-1", Focus("s-1", "phone-1", Map("card" -> "m1"), t(1), 1L)): Unit
    kit.change("s-1", Focus("s-1", "phone-1", Map("card" -> "m2"), t(2), 2L)): Unit
    assertEquals(kit.row("s-1").map(_.fields), Some(Map("card" -> "m2")))
  }
