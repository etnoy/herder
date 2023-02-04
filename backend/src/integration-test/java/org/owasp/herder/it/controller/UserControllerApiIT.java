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
package org.owasp.herder.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("UserController API integration tests")
class UserControllerApiIT extends BaseIT {

  @Autowired
  UserService userService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("Can list users as admin")
  void listUsers_IsAdmin_ReturnsUserList() {
    HashSet<String> userIdSet = new HashSet<>();

    userIdSet.add(integrationTestUtils.createTestAdmin());

    final String token = integrationTestUtils.getTokenFromAPILogin(
      TestConstants.TEST_ADMIN_LOGIN_NAME,
      TestConstants.TEST_ADMIN_PASSWORD
    );

    userIdSet.add(userService.create("Test User 2").block());
    userIdSet.add(userService.create("Test User 3").block());
    userIdSet.add(userService.create("Test User 4").block());

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
          .expectHeader()
          .contentType(MediaType.APPLICATION_JSON)
          .returnResult(UserEntity.class)
          .getResponseBody()
          .map(UserEntity::getId)
      )
      .recordWith(HashSet::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(users -> assertThat(users).containsExactlyElementsOf(userIdSet))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can deny list users as user")
  void listUsers_IsUser_ReturnsUserList() {
    HashSet<String> userIdSet = new HashSet<>();

    userIdSet.add(integrationTestUtils.createTestUser());

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

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();
  }
}
