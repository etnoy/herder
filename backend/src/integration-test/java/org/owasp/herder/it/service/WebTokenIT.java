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
package org.owasp.herder.it.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.WebTokenClock;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@DisplayName("Web Token integration tests")
class WebTokenIT extends BaseIT {

  @Autowired WebTokenService webTokenService;

  @Autowired WebTokenKeyManager webTokenKeyManager;

  @Autowired UserService userService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @MockBean WebTokenClock webTokenClock;

  @Test
  @DisplayName("A token that is about to expire should still be valid")
  void canAcceptTokensThatHaveNotExpiredYet() {
    integrationTestUtils.createTestUser();

    setClock(TestConstants.year2000Clock);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

    final Clock rightBeforeTheTokenExpires =
        Clock.fixed(
            TestConstants.year2000Clock
                .instant()
                .plusMillis(webTokenService.getExpirationTime() - 1),
            ZoneId.of("Z"));

    // Set the clock to 1 second before the token expires
    setClock(rightBeforeTheTokenExpires);

    assertThatCode(
            () -> {
              webTokenService.parseToken(accessToken);
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("It should be possible to generate a new token after an old one was invalidated")
  void canGenerateNewAccessTokenAfterOldTokenIsInvalidated() {
    final String userId = integrationTestUtils.createTestUser();

    // Create a token (we don't save it)
    integrationTestUtils.performAPILoginWithToken(
        TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

    // Invalidate the token
    webTokenKeyManager.invalidateAccessToken(userId);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

    final Authentication authentication = webTokenService.parseToken(accessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);

    // AssertJ cannot do list asserts on Collection<? extends ... >
    @SuppressWarnings("unchecked")
    Collection<SimpleGrantedAuthority> authorityList =
        (Collection<SimpleGrantedAuthority>) authentication.getAuthorities();
    assertThat(authorityList).containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  @DisplayName("A generated admin access token should be valid")
  void canGenerateValidAdminAccessTokens() {
    final String userId = integrationTestUtils.createTestAdmin();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);
    Authentication authentication = webTokenService.parseToken(accessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);

    // AssertJ cannot do list asserts on Collection<? extends ... >
    @SuppressWarnings("unchecked")
    Collection<SimpleGrantedAuthority> authorityList =
        (Collection<SimpleGrantedAuthority>) authentication.getAuthorities();
    assertThat(authorityList)
        .containsExactlyInAnyOrder(
            new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  @Test
  @DisplayName("A generated user access token should be valid")
  void canGenerateValidUserAccessTokens() {
    final String userId = integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);
    Authentication authentication = webTokenService.parseToken(accessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);

    // AssertJ cannot do list asserts on Collection<? extends ... >
    @SuppressWarnings("unchecked")
    Collection<SimpleGrantedAuthority> authorityList =
        (Collection<SimpleGrantedAuthority>) authentication.getAuthorities();
    assertThat(authorityList).containsExactly(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Test
  @DisplayName("An invalidated token should be rejected")
  void canInvalidateAccessTokens() {
    final String userId = integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

    // Invalidate the token
    webTokenKeyManager.invalidateAccessToken(userId);

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(accessToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Invalid token");
  }

  @Test
  @DisplayName("An expired token should be rejected")
  void canRejectTokensThatHaveExpired() {
    integrationTestUtils.createTestUser();

    setClock(TestConstants.year2000Clock);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

    final Clock rightAfterTheTokenExpires =
        Clock.fixed(
            TestConstants.year2000Clock
                .instant()
                .plusMillis(webTokenService.getExpirationTime() + 1),
            ZoneId.systemDefault());

    // Set the clock to 1 second after the token expires
    setClock(rightAfterTheTokenExpires);

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(accessToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Invalid token");
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
    resetClock();
  }

  private void setClock(final Clock testClock) {
    when(webTokenClock.now())
        .thenReturn(Date.from(LocalDateTime.now(testClock).atZone(ZoneId.of("Z")).toInstant()));
  }

  private void resetClock() {
    when(webTokenClock.now())
        .thenReturn(
            Date.from(
                LocalDateTime.now(Clock.systemDefaultZone())
                    .atZone(ZoneId.systemDefault())
                    .toInstant()));
  }
}
