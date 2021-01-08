/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.WebTokenService;
import io.jsonwebtoken.Clock;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebTokenService unit test")
class WebTokenServiceTest {
  private WebTokenService webTokenService;

  public class FixedClock implements Clock {

    private final Date now;

    public FixedClock(Date now) {
      this.now = now;
    }

    @Override
    public Date now() {
      return this.now;
    }
  }

  @Test
  void generateToken_ValidUserId_GeneratesValidToken() {
    final long testUserId = 293L;
    final String token = webTokenService.generateToken(testUserId);
    assertThat(token.length()).isGreaterThan(10);
  }

  @Test
  void getUserIdFromToken_ValidUserId_UserIdMatches() {
    final long testUserId = 846L;
    final String token = webTokenService.generateToken(testUserId);
    final long userId = webTokenService.getUserIdFromToken(token);
    assertThat(userId).isEqualTo(testUserId);
  }

  private void setClock(final Clock clock) {
    webTokenService.setClock(clock);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    webTokenService = new WebTokenService();
  }

  @Test
  void validateToken_ExpiredToken_NotValid() {
    final long mockUserId = 843L;

    final Clock longAgoClock = new FixedClock(Date.from(Instant.parse("2000-01-01T10:00:00.00Z")));
    setClock(longAgoClock);

    final String token = webTokenService.generateToken(mockUserId);
    final Clock tenYearsLaterClock =
        new FixedClock(Date.from(Instant.parse("2010-01-01T10:00:00.00Z")));

    setClock(tenYearsLaterClock);
    assertThat(webTokenService.validateToken(token)).isFalse();
  }

  @Test
  void validateToken_InvalidSignaturee_NotValid() {
    final long mockUserId = 930L;

    final String token = webTokenService.generateToken(mockUserId) + "blargh";
    assertThat(webTokenService.validateToken(token)).isFalse();
  }

  @Test
  void validateToken_TokenExpiresInTenYears_Valid() {
    final long mockUserId = 843L;

    final Clock farfutureClock =
        new FixedClock(Date.from(Instant.parse("2010-01-01T10:00:00.00Z")));
    setClock(farfutureClock);

    final String token = webTokenService.generateToken(mockUserId);
    final Clock longAgoClock = new FixedClock(Date.from(Instant.parse("2000-01-01T10:00:00.00Z")));

    setClock(longAgoClock);
    assertThat(webTokenService.validateToken(token)).isTrue();
  }

  @Test
  void validateToken_TokenExpiresRightNow_Valid() {
    final long mockUserId = 843L;

    final Clock fixedClock = new FixedClock(Date.from(Instant.parse("2000-01-01T10:00:00.00Z")));
    setClock(fixedClock);

    final String token = webTokenService.generateToken(mockUserId);
    assertThat(webTokenService.validateToken(token)).isTrue();
  }

  @Test
  void validateToken_ValidToken_GeneratesValidToken() {
    final long mockUserId = 843L;
    final String token = webTokenService.generateToken(mockUserId);
    assertThat(webTokenService.validateToken(token)).isTrue();
  }
}
