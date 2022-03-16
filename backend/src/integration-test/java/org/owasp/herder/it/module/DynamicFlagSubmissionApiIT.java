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
package org.owasp.herder.it.module;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleController;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("Dynamic flag submission API integration tests")
class DynamicFlagSubmissionApiIT extends BaseIT {
  @Nested
  @DisplayName("A valid dynamic flag")
  class ValidDynamicFlag {

    @Test
    @DisplayName("should be accepted")
    void canAcceptValidDynamicFlag() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagApiAndReturnSubmission(
                      TestConstants.TEST_MODULE_LOCATOR, token, dynamicFlag)
                  .map(Submission::isValid))
          .expectNext(true)
          .verifyComplete();
    }

    @Test
    @DisplayName("should be accepted when in lowercase")
    void canAcceptValidDynamicFlagInLowercase() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagApiAndReturnSubmission(
                      TestConstants.TEST_MODULE_LOCATOR, token, dynamicFlag.toLowerCase())
                  .map(Submission::isValid))
          .expectNext(true)
          .verifyComplete();
    }

    @Test
    @DisplayName("should be accepted when in uppercase")
    void canAcceptValidDynamicFlagInUppercase() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagApiAndReturnSubmission(
                      TestConstants.TEST_MODULE_LOCATOR, token, dynamicFlag.toUpperCase())
                  .map(Submission::isValid))
          .expectNext(true)
          .verifyComplete();
    }
  }

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired WebTestClient webTestClient;

  @Autowired ObjectMapper objectMapper;

  @Autowired FlagHandler flagHandler;

  @Autowired ModuleController moduleController;

  @Autowired PasswordEncoder passwordEncoder;

  @Autowired IntegrationTestUtils integrationTestUtils;

  private String userId;

  private String token;

  private String dynamicFlag;

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#testStringProvider")
  @DisplayName("An invalid dynamic flag should be rejected")
  void canRejectInvalidDynamicFlag(final String testString) {
    StepVerifier.create(
            integrationTestUtils
                .submitFlagApiAndReturnSubmission(TestConstants.TEST_MODULE_LOCATOR, token, testString)
                .map(Submission::isValid))
        .expectNext(false)
        .verifyComplete();
  }

  @Test
  @DisplayName("A dynamic flag submission should return 401 when not logged in")
  void canRejectUnauthorizedDynamicFlag() {
    integrationTestUtils
        .submitFlagApi(TestConstants.TEST_MODULE_LOCATOR, null, dynamicFlag)
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("An empty dynamic flag submission should return 401 when not logged in")
  void canRejectUnauthorizedEmptyDynamicFlag() {
    integrationTestUtils
        .submitFlagApi(TestConstants.TEST_MODULE_LOCATOR, null, "")
        .expectStatus()
        .isUnauthorized();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    userId = integrationTestUtils.createTestUser();
    token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    integrationTestUtils.createDynamicTestModule();
    dynamicFlag = flagHandler.getDynamicFlag(userId, TestConstants.TEST_MODULE_LOCATOR).block();
  }
}
