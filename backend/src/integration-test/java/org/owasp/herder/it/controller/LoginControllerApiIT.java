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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("UserController API integration tests")
class LoginControllerApiIT extends BaseIT {

  @Autowired
  UserService userService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("Can register user via API")
  void register_ValidData_ReturnsValidUser() {
    final String userId = webTestClient
      .post()
      .uri("/api/v1/register")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromPublisher(
          Mono.just(
            "{\"displayName\": \"" +
            TestConstants.TEST_USER_DISPLAY_NAME +
            "\", \"userName\": \"" +
            TestConstants.TEST_USER_LOGIN_NAME +
            "\",  \"password\": \"" +
            TestConstants.TEST_USER_PASSWORD +
            "\"}"
          ),
          String.class
        )
      )
      .exchange()
      .expectStatus()
      .isCreated()
      .expectBody(String.class)
      .returnResult()
      .getResponseBody();

    StepVerifier
      .create(userService.getById(userId))
      .assertNext(user -> {
        assertThat(user.getDisplayName()).isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
        assertThat(user.getId()).isEqualTo(userId);
      })
      .verifyComplete();

    StepVerifier
      .create(userService.getPasswordAuthByUserId(userId))
      .assertNext(passwordAuth -> {
        assertThat(passwordAuth.getLoginName()).isEqualTo(TestConstants.TEST_USER_LOGIN_NAME);
        assertThat(passwordAuth.getHashedPassword()).hasSizeGreaterThan(20);
        assertThat(passwordAuth.getHashedPassword()).contains("$");
      })
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();
  }
}
