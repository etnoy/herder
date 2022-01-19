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
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.authentication.LoginController;
import org.owasp.herder.authentication.LoginResponse;
import org.owasp.herder.authentication.PasswordLoginDto;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("WebTokenService integration tests")
class WebTokenServiceIT {

  private static final String testUserDisplayName = "Test User";

  private static final String testUserLoginName = "testuser";

  private static final String testUserPassword = "password123";

  @Autowired WebTokenService webTokenService;

  @Autowired WebTokenKeyManager webTokenKeyManager;

  @Autowired LoginController loginController;

  @Autowired UserService userService;

  @Autowired TestUtils testService;

  @Test
  void canGenerateAndInvalidateTokens() {

    final Clock testingClock =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));
    setClock(testingClock);

    final PasswordRegistrationDto passwordRegistrationDto =
        new PasswordRegistrationDto(testUserDisplayName, testUserLoginName, testUserPassword);

    loginController.register(passwordRegistrationDto).block();

    PasswordLoginDto passwordLoginDto = new PasswordLoginDto(testUserLoginName, testUserPassword);

    final long testUserId = userService.findUserIdByLoginName(testUserLoginName).block();

    ResponseEntity<LoginResponse> loginResponse = loginController.login(passwordLoginDto).block();

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    final String userAccessToken = loginResponse.getBody().getAccessToken();

    Authentication authentication = webTokenService.parseToken(userAccessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(testUserId);
    assertThat(authentication.getCredentials()).isEqualTo(userAccessToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isFalse();

    webTokenKeyManager.invalidateAccessToken(testUserId);

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(userAccessToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Invalid token");

    loginResponse = loginController.login(passwordLoginDto).block();
    final String userAccessToken2 = loginResponse.getBody().getAccessToken();

    authentication = webTokenService.parseToken(userAccessToken2);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(testUserId);
    assertThat(authentication.getCredentials()).isEqualTo(userAccessToken2);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isFalse();
  }

  @BeforeEach
  private void clear() {
    testService.deleteAll().block();
  }

  private void setClock(final Clock clock) {
    webTokenService.setClock(clock);
  }

  @Test
  void canExpireTokensAfterAWhile() {
    Clock testingClock = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));
    setClock(testingClock);

    final PasswordRegistrationDto passwordRegistrationDto =
        new PasswordRegistrationDto(testUserDisplayName, testUserLoginName, testUserPassword);

    loginController.register(passwordRegistrationDto).block();

    PasswordLoginDto passwordLoginDto = new PasswordLoginDto(testUserLoginName, testUserPassword);

    final long testUserId = userService.findUserIdByLoginName(testUserLoginName).block();

    ResponseEntity<LoginResponse> loginResponse = loginController.login(passwordLoginDto).block();

    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    final String userAccessToken = loginResponse.getBody().getAccessToken();

    Authentication authentication = webTokenService.parseToken(userAccessToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(testUserId);
    assertThat(authentication.getCredentials()).isEqualTo(userAccessToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")))
        .isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isFalse();

    // Right before the token expires
    testingClock =
        Clock.fixed(
            testingClock.instant().plusMillis(webTokenService.getExpirationTime() - 1),
            ZoneId.of("Z"));

    setClock(testingClock);

    assertThatCode(
            () -> {
              webTokenService.parseToken(userAccessToken);
            })
        .doesNotThrowAnyException();

    // Right after the token expires
    testingClock =
        Clock.fixed(
            testingClock.instant().plusMillis(webTokenService.getExpirationTime()), ZoneId.of("Z"));

    setClock(testingClock);

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(userAccessToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Invalid token");
  }
}
