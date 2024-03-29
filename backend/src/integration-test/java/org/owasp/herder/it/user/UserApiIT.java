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
package org.owasp.herder.it.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@DisplayName("User API integration tests")
class UserApiIT extends BaseIT {

  @Autowired
  UserService userService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  WebTokenKeyManager webTokenKeyManager;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("A regular user cannot list users")
  void canDenyListUsersIfNotAdmin() {
    integrationTestUtils.createTestUser();
    userService.create("User1").block();
    userService.create("User2").block();
    userService.create("User3").block();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );

    webTestClient
      .get()
      .uri("/api/v1/users")
      .header("Authorization", "Bearer " + token)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .isForbidden();
  }

  @Test
  @DisplayName("A regular user cannot get info on another user")
  void canDenyOtherUsersInformation() {
    integrationTestUtils.createTestUser();

    final String userId = userService.create("User2").block();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );

    webTestClient
      .get()
      .uri(String.format("/api/v1/user/%s", userId))
      .header("Authorization", "Bearer " + token)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .isForbidden();
  }

  @Test
  @DisplayName("An admin can get any user's information")
  void canGetOtherUsersInformationIfAdmin() {
    integrationTestUtils.createTestAdmin();
    final String userId = userService.create("User2").block();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_ADMIN_LOGIN_NAME,
      TestConstants.TEST_ADMIN_PASSWORD
    );

    StepVerifier
      .create(
        webTestClient
          .get()
          .uri(String.format("/api/v1/user/%s", userId))
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .returnResult(UserEntity.class)
          .getResponseBody()
          .map(UserEntity::getDisplayName)
      )
      .expectNext("User2")
      .verifyComplete();
  }

  @Test
  @DisplayName("A regular user can get their own user information")
  void canGetOwnUserInformation() {
    final String userId = integrationTestUtils.createTestUser();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );

    StepVerifier
      .create(
        webTestClient
          .get()
          .uri(String.format("/api/v1/user/%s", userId))
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .returnResult(UserEntity.class)
          .getResponseBody()
      )
      .assertNext(user -> {
        assertThat(user.getDisplayName()).isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("An administrator can list users")
  void canListUsersAsAdmin() {
    integrationTestUtils.createTestAdmin();
    userService.create("User1").block();
    userService.create("User2").block();
    userService.create("User3").block();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_ADMIN_LOGIN_NAME,
      TestConstants.TEST_ADMIN_PASSWORD
    );

    StepVerifier
      .create(
        webTestClient
          .get()
          .uri("/api/v1/users")
          .header("Authorization", "Bearer " + token)
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus()
          .isOk()
          .returnResult(UserEntity.class)
          .getResponseBody()
      )
      .expectNextCount(4)
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();
  }

  @Test
  @DisplayName("Can return HTTP 400 for an invalid user id")
  void canReturn404ForInvalidUserId() {
    integrationTestUtils.createTestAdmin();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_ADMIN_LOGIN_NAME,
      TestConstants.TEST_ADMIN_PASSWORD
    );

    final String invalidUserId = "XYZ";

    webTestClient
      .get()
      .uri(String.format("/api/v1/user/%s", invalidUserId))
      .header("Authorization", "Bearer " + token)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .isNotFound();
  }

  @Test
  @DisplayName("Can return HTTP 404 for a nonexistent user id")
  void canReturn404ForNonExistentUserId() {
    integrationTestUtils.createTestAdmin();

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_ADMIN_LOGIN_NAME,
      TestConstants.TEST_ADMIN_PASSWORD
    );

    final String invalidUserId = "fbcdef123456789012345678";

    webTestClient
      .get()
      .uri(String.format("/api/v1/user/%s", invalidUserId))
      .header("Authorization", "Bearer " + token)
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus()
      .isNotFound();
  }
}
