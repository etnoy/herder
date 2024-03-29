/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
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

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.owasp.herder.crypto.WebTokenClock;
import org.owasp.herder.model.ClassEntity;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.scoring.SanitizedRankedSubmission;
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;

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
    Long.MIN_VALUE,
  };

  public static final Long[] LONGS_WITH_NULL = ArrayUtils.addAll(LONGS, (Long) null);

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
    LocalDateTime.MAX,
  };

  public static final LocalDateTime[] LOCALDATETIMES_WITH_NULL = ArrayUtils.addAll(
    LOCALDATETIMES,
    (LocalDateTime) null
  );

  public static final byte[] TEST_BYTE_ARRAY = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
  public static final byte[] TEST_BYTE_ARRAY2 = { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };
  public static final byte[] TEST_BYTE_ARRAY3 = { 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17, 127 };
  public static final byte[] TEST_BYTE_ARRAY4 = { 4, 5, 6, 7, 8, 11, 10, 11, 12, 16, 17, 127 };
  public static final byte[] TEST_BYTE_ARRAY5 = { 4, 5, 6, 7, 8, 13, 10, 11, 12, 16, 17, 127 };

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
    "﷽ ",
    "مُنَاقَشَةُ سُبُلِ اِسْتِخْدَامِ اللُّغَةِ فِي النُّظُمِ الْقَائِمَةِ وَفِيم يَخُصَّ التَّطْبِيقَاتُ الْحاسُوبِيَّةُ،",
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
    "﷽",
    "مُنَاقَشَةُ سُبُلِ اِسْتِخْدَامِ اللُّغَةِ فِي النُّظُمِ الْقَائِمَةِ وَفِيم يَخُصَّ التَّطْبِيقَاتُ الْحاسُوبِيَّةُ،",
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
    "﷽﷽  ﷽",
  };

  public static final String[] INVALID_NAMES = { "", null };

  public static final String[] STRINGS_WITH_NULL = ArrayUtils.addAll(STRINGS, (String) null);

  public static final String TEST_TEAM_ID = "abcdab123456789012345678";
  public static final String TEST_TEAM_DISPLAY_NAME = "Test Team";

  public static final String TEST_ADMIN_DISPLAY_NAME = "Test Admin";
  public static final String TEST_ADMIN_LOGIN_NAME = "test-admin-name";
  public static final String TEST_ADMIN_PASSWORD = "test";

  public static final String TEST_USER_DISPLAY_NAME = "Test User";
  public static final String TEST_USER_LOGIN_NAME = "test-login-name";
  public static final String TEST_USER_PASSWORD = "test";

  // The password "test" hashed with BCrypt
  public static final String HASHED_TEST_PASSWORD = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

  public static final Key TEST_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS512);

  public static final String TEST_MODULE_LOCATOR = "test-module";
  public static final String TEST_MODULE_ID = "abcdef123456789012345678";
  public static final String TEST_MODULE_NAME = "Test Module";

  public static final String TEST_MODULE_TAG_NAME = "test-tag";

  public static final String TEST_STATIC_FLAG = "Static Test Flag 123 456";
  public static final String TEST_DYNAMIC_FLAG = "flag{123456789abcdef}";

  public static final String TEST_USER_ID = "abcdef123456789012345678";
  public static final String TEST_USER_ID2 = "abcdef123456789012345679";

  public static final String TEST_CLASS_ID = "abcdef1234567890abcd5678";
  public static final String TEST_CLASS_NAME = "Test Class";

  public static final String TEST_CSRF_PSEUDONYM = "a-target";

  public static final String VERY_LONG_STRING =
    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  public static final Clock YEAR_2000_CLOCK = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));
  public static final WebTokenClock YEAR_2000_WEB_TOKEN_CLOCK = new WebTokenClock(YEAR_2000_CLOCK);

  public static final Clock YEAR_2100_CLOCK = Clock.fixed(Instant.parse("2100-01-01T10:00:00.00Z"), ZoneId.of("Z"));
  public static final WebTokenClock YEAR_2100_WEB_TOKEN_CLOCK = new WebTokenClock(YEAR_2100_CLOCK);

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

  public static final UserEntity TEST_USER_ENTITY = UserEntity
    .builder()
    .key(TEST_BYTE_ARRAY)
    .displayName(TEST_USER_DISPLAY_NAME)
    .build();

  public static final ClassEntity TEST_CLASS_ENTITY = ClassEntity.builder().name(TEST_CLASS_NAME).build();

  public static final TeamEntity TEST_TEAM_ENTITY = TeamEntity
    .builder()
    .displayName(TEST_TEAM_DISPLAY_NAME)
    .members(new ArrayList<UserEntity>())
    .build();

  public static final ModuleEntity TEST_MODULE_ENTITY = ModuleEntity
    .builder()
    .name(TestConstants.TEST_MODULE_NAME)
    .locator(TEST_MODULE_LOCATOR)
    .build();

  public static final RankedSubmission TEST_RANKED_SUBMISSION = RankedSubmission
    .builder()
    .module(TEST_MODULE_ENTITY)
    .user(TEST_USER_ENTITY.withId(TEST_USER_ID))
    .rank(1L)
    .time(LocalDateTime.now(YEAR_2000_CLOCK))
    .baseScore(100L)
    .bonusScore(50L)
    .score(150L)
    .build();

  public static final SanitizedRankedSubmission TEST_SANITIZED_RANKED_SUBMISSION = SanitizedRankedSubmission
    .builder()
    .id(TEST_USER_ID)
    .solverType(SolverType.USER)
    .displayName(TEST_USER_DISPLAY_NAME)
    .moduleLocator(TEST_MODULE_LOCATOR)
    .moduleName(TEST_MODULE_NAME)
    .rank(1L)
    .time(LocalDateTime.now(YEAR_2000_CLOCK))
    .baseScore(100L)
    .bonusScore(50L)
    .score(150L)
    .build();
}
