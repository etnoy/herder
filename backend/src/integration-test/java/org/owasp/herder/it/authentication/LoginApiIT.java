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
package org.owasp.herder.it.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

@DisplayName("Login API integration tests")
class LoginApiIT extends BaseIT {

  @Autowired
  UserService userService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  WebTokenKeyManager webTokenKeyManager;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  private String userId;

  @Test
  @DisplayName("Logging in with correct credentials should return a valid token")
  void canLoginWithValidCredentials() {
    final String token = integrationTestUtils.performAPILoginWithToken(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );

    final Claims claims = Jwts
      .parserBuilder()
      .setSigningKey(webTokenKeyManager.getKeyForUser(userId))
      .build()
      .parseClaimsJws(token)
      .getBody();

    assertThat(claims.getSubject()).isEqualTo(userId);
  }

  @Test
  @DisplayName("Logging in with an empty password should return HTTP Bad Request")
  void canReturn400WhenLogginInWithEmptyPassword() {
    integrationTestUtils.performAPILogin(TestConstants.TEST_USER_LOGIN_NAME, "").expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Logging in with an empty username should return HTTP Unauthorized")
  void canReturn400WhenLogginInWithEmptyUsername() {
    integrationTestUtils.performAPILogin("", TestConstants.TEST_USER_PASSWORD).expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Logging in with an incorrect password should return HTTP Unauthorized")
  void canReturn401WhenLogginInWithWrongPassword() {
    integrationTestUtils.performAPILogin(TestConstants.TEST_USER_LOGIN_NAME, "wrong").expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Logging in with an incorrect username should return HTTP Unauthorized")
  void canReturn401WhenLogginInWithWrongUsername() {
    integrationTestUtils.performAPILogin("wrong", TestConstants.TEST_USER_PASSWORD).expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Logging in with an incorrect username and password should return HTTP Unauthorized")
  void canReturn401WhenLogginInWithWrongUsernameAndPassword() {
    integrationTestUtils.performAPILogin("wrong", "still wrong").expectStatus().isUnauthorized();
  }

  @BeforeEach
  void setUp() {
    integrationTestUtils.resetState();

    userId = integrationTestUtils.createTestUser();
  }
}
