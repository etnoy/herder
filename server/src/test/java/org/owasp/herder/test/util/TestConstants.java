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

import org.apache.commons.lang3.ArrayUtils;
import org.owasp.herder.crypto.WebTokenClock;

public final class TestConstants {

  public static final long[] INVALID_IDS = {-1L, -1000L, 0L, -1234567L, -500L};

  public static final boolean INITIAL_BOOLEAN = false;

  public static final boolean[] BOOLEANS = {false, true};

  public static final Boolean[] BOOLEANS_WITH_NULL = {null, false, true};

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
    LocalDateTime.MAX
  };

  public static final LocalDateTime[] LOCALDATETIMES_WITH_NULL =
      ArrayUtils.addAll(LOCALDATETIMES, (LocalDateTime) null);

  public static final String INITIAL_STRING = "";

  public static final String[] STRINGS = {
    INITIAL_STRING,
    "Test String",
    "åäö",
    "me@example.com",
    "1;DROP TABLE users",
    " ",
    "%",
    "_",
    "-",
    "--",
    "జ్ఞ‌ా",
    "Ｔｈｅ ｑｕｉｃｋ ｂｒｏｗｎ ｆｏｘ ｊｕｍｐｓ ｏｖｅｒ ｔｈｅ ｌａｚｙ ｄｏｇ",
    "𝐓𝐡𝐞 𝐪𝐮𝐢𝐜𝐤 𝐛𝐫𝐨𝐰𝐧 𝐟𝐨𝐱 𝐣𝐮𝐦𝐩𝐬 𝐨𝐯𝐞𝐫 𝐭𝐡𝐞 𝐥𝐚𝐳𝐲 𝐝𝐨𝐠",
    "𝕿𝖍𝖊 𝖖𝖚𝖎𝖈𝖐 𝖇𝖗𝖔𝖜𝖓 𝖋𝖔𝖝 𝖏𝖚𝖒𝖕𝖘 𝖔𝖛𝖊𝖗 𝖙𝖍𝖊 𝖑𝖆𝖟𝖞 𝖉𝖔𝖌",
    "𝑻𝒉𝒆 𝒒𝒖𝒊𝒄𝒌 𝒃𝒓𝒐𝒘𝒏 𝒇𝒐𝒙 𝒋𝒖𝒎𝒑𝒔 𝒐𝒗𝒆𝒓 𝒕𝒉𝒆 𝒍𝒂𝒛𝒚 𝒅𝒐𝒈",
    "𝓣𝓱𝓮 𝓺𝓾𝓲𝓬𝓴 𝓫𝓻𝓸𝔀𝓷 𝓯𝓸𝔁 𝓳𝓾𝓶𝓹𝓼 𝓸𝓿𝓮𝓻 𝓽𝓱𝓮 𝓵𝓪𝔃𝔂 𝓭𝓸𝓰",
    "𝕋𝕙𝕖 𝕢𝕦𝕚𝕔𝕜 𝕓𝕣𝕠𝕨𝕟 𝕗𝕠𝕩 𝕛𝕦𝕞𝕡𝕤 𝕠𝕧𝕖𝕣 𝕥𝕙𝕖 𝕝𝕒𝕫𝕪 𝕕𝕠𝕘",
    "𝚃𝚑𝚎 𝚚𝚞𝚒𝚌𝚔 𝚋𝚛𝚘𝚠𝚗 𝚏𝚘𝚡 𝚓𝚞𝚖𝚙𝚜 𝚘𝚟𝚎𝚛 𝚝𝚑𝚎 𝚕𝚊𝚣𝚢 𝚍𝚘𝚐",
    "😍",
    "👩🏽",
    "👾 🙇 💁 🙅 🙆 🙋 🙎 🙍",
    "🐵 🙈 🙉 🙊",
    "❤️ 💔 💌 💕 💞 💓 💗 💖 💘 💝 💟 💜 💛 💚 💙",
    "✋🏿 💪🏿 👐🏿 🙌🏿 👏🏿 🙏🏿",
    "🚾 🆒 🆓 🆕 🆖 🆗 🆙 🏧",
    "0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟",
    "ثم نفس سقطت وبالتحديد،, جزيرتي باستخدام أن دنو. إذ هنا؟ الستار وتنصيب كان. أهّل ايطاليا، بريطانيا-فرنسا قد أخذ. سليمان، إتفاقية بين ما, يذكر الحدود أي بعد, معاملة بولندا، الإطلاق عل إيو.",
    "בְּרֵאשִׁי  בָּרָא אֱלֹהִים, אֵת הַשָּׁמַיִם, וְאֵת        הָיְתָהtest",
    " ﷽ ",
    "ﷺ",
    "مُنَاقَشَةُ سُبُلِ اِسْتِخْدَامِ اللُّغَةِ فِي النُّظُمِ الْقَائِمَةِ وَفِيم يَخُصَّ التَّطْبِيقَاتُ الْحاسُوبِيَّةُ، "
  };

  public static final String INITIAL_NAME = "id";

  public static final String[] NAMES = {INITIAL_NAME, "id-with-hyphen", "abc123"};

  public static final String[] INVALID_NAMES = {"", null};

  public static final String[] STRINGS_WITH_NULL = ArrayUtils.addAll(STRINGS, (String) null);

  public static final String TEST_DISPLAY_NAME = "Test User";
  public static final String TEST_LOGIN_NAME = "test";
  public static final String TEST_PASSWORD = "test";

  // The password "test" hashed with BCrypt
  public static final String HASHED_TEST_PASSWORD =
      "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

  public static final String TEST_MODULE_NAME = "Test Module";
  public static final String TEST_STATIC_FLAG = "Static Test Flag 123 456";

  public static final Clock longAgoClock =
      Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());
  public static final WebTokenClock year2000WebTokenClock = new WebTokenClock(longAgoClock);

  public static final Clock year2100Clock =
      Clock.fixed(Instant.parse("2100-01-01T10:00:00.00Z"), ZoneId.systemDefault());
  public static final WebTokenClock year2100WebTokenClock = new WebTokenClock(year2100Clock);
}
