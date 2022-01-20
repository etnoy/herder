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

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Web Token integration tests")
class WebTokenIT {

  @Autowired WebTokenService webTokenService;

  @Autowired WebTokenKeyManager webTokenKeyManager;

  @Autowired UserService userService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("A token that is about to expired should still be valid")
  void canAcceptTokensThatHaveNotExpiredYet() {
    integrationTestUtils.createTestUser();

    setClock(TestConstants.longAgoClock);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    final Clock rightBeforeTheTokenExpires =
        Clock.fixed(
            TestConstants.longAgoClock
                .instant()
                .plusMillis(webTokenService.getExpirationTime() - 1),
            ZoneId.systemDefault());

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
    final long userId = integrationTestUtils.createTestUser();

    // Create a token (we don't save it)
    integrationTestUtils.performAPILoginWithToken(
        TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    // Invalidate the token
    webTokenKeyManager.invalidateAccessToken(userId);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    final Authentication authentication = webTokenService.parseToken(accessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isFalse();
    assertThat(authentication.getAuthorities()).hasSize(1);
  }

  @Test
  @DisplayName("A generated admin access token should be valid")
  void canGenerateValidAdminAccessTokens() {
    final long userId = integrationTestUtils.createTestAdmin();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);
    Authentication authentication = webTokenService.parseToken(accessToken);
    System.out.println(userService.findUserAuthByUserId(userId).block());

    System.out.println(authentication.toString());
    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isTrue();
    assertThat(authentication.getAuthorities()).hasSize(2);
  }

  @Test
  @DisplayName("A generated user access token should be valid")
  void canGenerateValidUserAccessTokens() {
    final long userId = integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);
    Authentication authentication = webTokenService.parseToken(accessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(userId);
    assertThat(authentication.getCredentials()).isEqualTo(accessToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isFalse();
    assertThat(authentication.getAuthorities()).hasSize(1);
  }

  @Test
  @DisplayName("An invalidated token should be rejected")
  void canInvalidateAccessTokens() {
    final long userId = integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

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

    setClock(TestConstants.longAgoClock);

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    final Clock rightAfterTheTokenExpires =
        Clock.fixed(
            TestConstants.longAgoClock
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
  private void clear() {
    integrationTestUtils.resetState();
  }

  private void setClock(final Clock clock) {
    webTokenService.setClock(clock);
  }
}
