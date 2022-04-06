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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("Scoreboard API integration tests")
class ScoreboardApiIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired WebTestClient webTestClient;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("Can return an empty submission list for module")
  void canReturnEmptySubmissionListForModule() {
    integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    integrationTestUtils.createStaticTestModule();

    StepVerifier.create(
            webTestClient
                .get()
                .uri("/api/v1/scoreboard/module/" + TestConstants.TEST_MODULE_LOCATOR)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(RankedSubmission.class)
                .getResponseBody())
        .verifyComplete();
  }

  @Test
  @DisplayName("Can return an empty submission list for user")
  void canReturnEmptySubmissionListForUser() {
    final String userId = integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    integrationTestUtils.createStaticTestModule();

    StepVerifier.create(
            webTestClient
                .get()
                .uri("/api/v1/scoreboard/user/" + userId)
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(RankedSubmission.class)
                .getResponseBody())
        .verifyComplete();
  }

  @Test
  @DisplayName("Can return 404 if module does not exist")
  void canReturn404IfModuleDoesNotExist() {
    integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    webTestClient
        .get()
        .uri("/api/v1/scoreboard/module/this-module-does-not-exist")
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
  }

  @Test
  @DisplayName("Can return 404 if user does not exist")
  void canReturn404IfUserDoesNotExist() {
    integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    webTestClient
        .get()
        .uri("/api/v1/scoreboard/user/" + TestConstants.TEST_MODULE_ID)
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  @DisplayName("Can return 400 if user id is invalid")
  void canReturn400IfUserIdIsInvalid() {
    integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    webTestClient
        .get()
        .uri("/api/v1/scoreboard/user/xyz")
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  @DisplayName("Can return 400 if module locator is invalid")
  void canReturn400IfModuleLocatorIsInvalid() {
    integrationTestUtils.createTestUser();

    final String token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    webTestClient
        .get()
        .uri("/api/v1/scoreboard/module/XYZ")
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
