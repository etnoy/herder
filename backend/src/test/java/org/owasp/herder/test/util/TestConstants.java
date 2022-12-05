/*
 * Copyright 2018-2022 Jonathan Jogenfors, jonathan@jogenfors.se
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.owasp.herder.test.util;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.owasp.herder.crypto.WebTokenClock;

public final class TestConstants {
  public static final boolean INITIAL_BOOLEAN = false;

  public static final boolean[] BOOLEANS = { false, true };

  public static final Boolean[] BOOLEANS_WITH_NULL = { null, false, true };

  public static final Long INITIAL_LONG = 0L;

  public static final Long[] LONGS = {
    INITIAL_LONG,
    1L,
    -1L,
    100L,
    -100L,
    1000L,
    -1000L,
    Long.valueOf(Integer.MAX_VALUE) + 1,
    Long.valueOf(Integer.MIN_VALUE - 1),
    123456789L,
    -12346789L,
    Long.MAX_VALUE,
    Long.MIN_VALUE
  };

  public static final Long[] LONGS_WITH_NULL = ArrayUtils.addAll(
    LONGS,
    (Long) null
  );

  public static final LocalDateTime INITIAL_LOCALDATETIME = LocalDateTime.MIN;

  public static final LocalDateTime[] LOCALDATETIMES = {
    INITIAL_LOCALDATETIME,
    INITIAL_LOCALDATETIME.plusNanos(1),
    INITIAL_LOCALDATETIME.plusSeconds(1),
    INITIAL_LOCALDATETIME.plusMinutes(1),
    INITIAL_LOCALDATETIME.plusHours(1),
    INITIAL_LOCALDATETIME.plusDays(1),
    INITIAL_LOCALDATETIME.plusWeeks(1),
    INITIAL_LOCALDATETIME.plusMonths(1),
    INITIAL_LOCALDATETIME.plusYears(1),
    INITIAL_LOCALDATETIME.plusYears(1000),
    INITIAL_LOCALDATETIME.plusYears(100000),
    LocalDateTime.MAX
  };

  public static final LocalDateTime[] LOCALDATETIMES_WITH_NULL = ArrayUtils.addAll(
    LOCALDATETIMES,
    (LocalDateTime) null
  );

  public static final byte[] TEST_BYTE_ARRAY = {
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10
  };

  public static final String[] STRINGS = {
    "Test",
    "åäö",
    "me@example.com",
    "1;DROP TABLE users",
    " ",
    "%",
    "_",
    "-",
    "𝕋𝕙𝕖 𝕢𝕦𝕚𝕔𝕜 𝕓𝕣𝕠𝕨𝕟 𝕗𝕠𝕩 𝕛𝕦𝕞𝕡𝕤 𝕠𝕧𝕖𝕣 𝕥𝕙𝕖 𝕝𝕒𝕫𝕪 𝕕𝕠𝕘",
    "❤️ 💔 💌 💕 💞 💓 💗 💖 💘 💝 💟 💜 💛 💚 💙",
    " ﷽ ",
    "مُنَاقَشَةُ سُبُلِ اِسْتِخْدَامِ اللُّغَةِ فِي النُّظُمِ الْقَائِمَةِ وَفِيم يَخُصَّ التَّطْبِيقَاتُ الْحاسُوبِيَّةُ، "
  };

  public static final String[] VALID_STATIC_FLAGS = {
    "flag",
    "åäöÅÄÖ",
    "me@example.com",
    "flag-without-whitespace",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "%",
    "𝕋𝕙𝕖 𝕢𝕦𝕚𝕔𝕜 𝕓𝕣𝕠𝕨𝕟 𝕗𝕠𝕩 𝕛𝕦𝕞𝕡𝕤 𝕠𝕧𝕖𝕣 𝕥𝕙𝕖 𝕝𝕒𝕫𝕪 𝕕𝕠𝕘",
    "❤️ 💔 💌 💕 💞 💓 💗 💖 💘 💝 💟 💜 💛 💚 💙",
    " ﷽ ",
    "مُنَاقَشَةُ سُبُلِ اِسْتِخْدَامِ اللُّغَةِ فِي النُّظُمِ الْقَائِمَةِ وَفِيم يَخُصَّ التَّطْبِيقَاتُ الْحاسُوبِيَّةُ، "
  };

  public static final String[] VALID_NAMES = {
    "Module Name",
    "aa",
    "me@example.com",
    "module-name-without-whitespace",
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "%*",
    "𝕋𝕙𝕖 𝕢𝕦𝕚𝕔𝕜 𝕓𝕣𝕠𝕨𝕟 𝕗𝕠𝕩 𝕛𝕦𝕞𝕡𝕤",
    "❤️ 💔 💌 💕 💞 💓 💗 💖 💘 💝 💟 💜 💛 💚 💙",
    " ﷽ ﷽  ﷽  "
  };

  public static final String INITIAL_NAME = "id";

  public static final String[] NAMES = {
    INITIAL_NAME,
    "id-with-hyphen",
    "abc123"
  };

  public static final String[] INVALID_NAMES = { "", null };

  public static final String[] STRINGS_WITH_NULL = ArrayUtils.addAll(
    STRINGS,
    (String) null
  );

  public static final String TEST_TEAM_DISPLAY_NAME = "Test Team";

  public static final String TEST_ADMIN_DISPLAY_NAME = "Test Admin";
  public static final String TEST_ADMIN_LOGIN_NAME = "test-admin-name";
  public static final String TEST_ADMIN_PASSWORD = "test";

  public static final String TEST_USER_DISPLAY_NAME = "Test User";
  public static final String TEST_USER_LOGIN_NAME = "test-login-name";
  public static final String TEST_USER_PASSWORD = "test";

  // The password "test" hashed with BCrypt
  public static final String HASHED_TEST_PASSWORD =
    "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

  public static final String TEST_MODULE_LOCATOR = "test-module";
  public static final String TEST_MODULE_ID = "abcdef123456789012345678";
  public static final String TEST_MODULE_NAME = "Test Module";

  public static final String TEST_MODULE_TAG_NAME = "test-tag";

  public static final String TEST_STATIC_FLAG = "Static Test Flag 123 456";

  public static final String TEST_USER_ID = "abcdef123456789012345678";

  public static final String TEST_CLASS_ID = "abcdef1234567890abcd5678";

  public static final String VERY_LONG_STRING =
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  public static final Clock year2000Clock = Clock.fixed(
    Instant.parse("2000-01-01T10:00:00.00Z"),
    ZoneId.of("Z")
  );
  public static final WebTokenClock year2000WebTokenClock = new WebTokenClock(
    year2000Clock
  );

  public static final Clock year2100Clock = Clock.fixed(
    Instant.parse("2100-01-01T10:00:00.00Z"),
    ZoneId.of("Z")
  );
  public static final WebTokenClock year2100WebTokenClock = new WebTokenClock(
    year2100Clock
  );

  public static Stream<String> testStringProvider() {
    return Stream.of(STRINGS);
  }

  public static Stream<String> validDisplayNameProvider() {
    return Stream.of(VALID_NAMES);
  }

  public static Stream<String> validModuleNameProvider() {
    return Stream.of(VALID_NAMES);
  }

  public static Stream<String> validStaticFlagProvider() {
    return Stream.of(VALID_STATIC_FLAGS);
  }
}
