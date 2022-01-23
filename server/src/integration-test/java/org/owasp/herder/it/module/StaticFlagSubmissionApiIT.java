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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleController;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Static flag submission API integration tests")
class StaticFlagSubmissionApiIT {
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

  private String token;

  @Nested
  @DisplayName("A valid static flag")
  class ValidStaticFlag {

    @Test
    @DisplayName("should be accepted")
    void canAcceptValidStaticFlag() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME, token, TestConstants.TEST_STATIC_FLAG)
                  .map(Submission::isValid))
          .expectNext(true)
          .expectComplete()
          .verify();
    }

    @Test
    @DisplayName("should be accepted when surrounded by spaces")
    void canAcceptValidStaticFlagIfSurroundedBySpaces() {
      final String flagWithSpaces = "     " + TestConstants.TEST_STATIC_FLAG + "         ";

      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME, token, flagWithSpaces)
                  .map(Submission::isValid))
          .expectNext(true)
          .expectComplete()
          .verify();
    }

    @Test
    @DisplayName("should be accepted when in lowercase")
    void canAcceptValidStaticFlagInLowercase() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME,
                      token,
                      TestConstants.TEST_STATIC_FLAG.toLowerCase())
                  .map(Submission::isValid))
          .expectNext(true)
          .expectComplete()
          .verify();
    }

    @Test
    @DisplayName("should be accepted when in uppercase")
    void canAcceptValidStaticFlagInUppercase() {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME,
                      token,
                      TestConstants.TEST_STATIC_FLAG.toUpperCase())
                  .map(Submission::isValid))
          .expectNext(true)
          .expectComplete()
          .verify();
    }

    @Test
    @DisplayName("should be rejected when surrounded by other whitespace")
    void canRejectValidStaticFlagIfSurroundedByOtherWhitespace() {
      final String flagWithOtherWhitespace = "\n" + TestConstants.TEST_STATIC_FLAG + "\t";

      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME, token, flagWithOtherWhitespace)
                  .map(Submission::isValid))
          .expectNext(false)
          .expectComplete()
          .verify();
    }
  }

  @Test
  @DisplayName("An invalid static flag should be rejected")
  void canRejectInvalidStaticFlag() {
    for (String invalidStaticFlag : TestConstants.STRINGS) {
      StepVerifier.create(
              integrationTestUtils
                  .submitFlagAndReturnSubmission(
                      TestConstants.TEST_MODULE_NAME, token, invalidStaticFlag)
                  .map(Submission::isValid))
          .expectNext(false)
          .expectComplete()
          .verify();
    }
  }

  @Test
  @DisplayName("An empty static flag submission should return 401 when not logged in")
  void canRejectUnauthorizedEmptyStaticFlag() {
    integrationTestUtils
        .submitFlag(TestConstants.TEST_MODULE_NAME, null, "")
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("A static flag submission should return 401 when not logged in")
  void canRejectUnauthorizedStaticFlag() {
    integrationTestUtils
        .submitFlag(TestConstants.TEST_MODULE_NAME, null, TestConstants.TEST_STATIC_FLAG)
        .expectStatus()
        .isUnauthorized();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    integrationTestUtils.createTestUser();
    token =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    integrationTestUtils.createStaticTestModule();
  }
}
