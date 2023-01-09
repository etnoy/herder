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
package org.owasp.herder.it.module;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
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
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@DisplayName("Flag submission API integration tests")
class FlagSubmissionApiIT extends BaseIT {

  @Nested
  @DisplayName("A dynamic flag")
  class DynamicFlag {

    @Nested
    @DisplayName("if valid")
    class ValidDynamicFlag {

      @Test
      @DisplayName("should be accepted")
      void canAcceptValidDynamicFlag() {
        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                dynamicFlag
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }

      @Test
      @DisplayName("should be accepted when in lowercase")
      void canAcceptValidDynamicFlagInLowercase() {
        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                dynamicFlag.toLowerCase()
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }

      @Test
      @DisplayName("should be accepted when in uppercase")
      void canAcceptValidDynamicFlagInUppercase() {
        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                dynamicFlag.toUpperCase()
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }
    }

    private String dynamicFlag;

    @ParameterizedTest
    @MethodSource("org.owasp.herder.test.util.TestConstants#testStringProvider")
    @DisplayName("should be rejected if invalid")
    void canRejectInvalidDynamicFlag(final String testString) {
      StepVerifier
        .create(
          integrationTestUtils
            .submitFlagApiAndReturnSubmission(
              TestConstants.TEST_MODULE_LOCATOR,
              token,
              testString
            )
            .map(Submission::isValid)
        )
        .expectNext(false)
        .verifyComplete();
    }

    @Test
    @DisplayName("should return HTTP 401 when not logged in")
    void canRejectUnauthorizedDynamicFlag() {
      integrationTestUtils
        .submitFlagApi(TestConstants.TEST_MODULE_LOCATOR, null, dynamicFlag)
        .expectStatus()
        .isUnauthorized();
    }

    @Test
    @DisplayName("when empty should return HTTP 401 when not logged in")
    void canRejectUnauthorizedEmptyDynamicFlag() {
      integrationTestUtils
        .submitFlagApi(TestConstants.TEST_MODULE_LOCATOR, null, "")
        .expectStatus()
        .isUnauthorized();
    }

    @BeforeEach
    void setup() {
      userId = integrationTestUtils.createTestUser();
      token =
        integrationTestUtils.performAPILoginWithToken(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        );

      integrationTestUtils.createDynamicTestModule();
      dynamicFlag =
        flagHandler
          .getDynamicFlag(userId, TestConstants.TEST_MODULE_LOCATOR)
          .block();
    }
  }

  @Nested
  @DisplayName("A static flag")
  class StaticFlagTests {

    @Nested
    @DisplayName("if valid")
    class ValidStaticFlag {

      @ParameterizedTest
      @MethodSource(
        "org.owasp.herder.test.util.TestConstants#validStaticFlagProvider"
      )
      @DisplayName("should be accepted")
      void canAcceptValidStaticFlag(final String flagToTest) {
        moduleService.setStaticFlag(moduleId, flagToTest).block();
        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                flagToTest
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }

      @ParameterizedTest
      @MethodSource(
        "org.owasp.herder.test.util.TestConstants#validStaticFlagProvider"
      )
      @DisplayName("should be accepted when in lowercase")
      void canAcceptValidStaticFlagInLowercase(final String flagToTest) {
        moduleService.setStaticFlag(moduleId, flagToTest).block();

        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                flagToTest.toLowerCase()
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }

      @ParameterizedTest
      @MethodSource(
        "org.owasp.herder.test.util.TestConstants#validStaticFlagProvider"
      )
      @DisplayName("should be accepted when in uppercase")
      void canAcceptValidStaticFlagInUppercase(final String flagToTest) {
        moduleService.setStaticFlag(moduleId, flagToTest).block();

        StepVerifier
          .create(
            integrationTestUtils
              .submitFlagApiAndReturnSubmission(
                TestConstants.TEST_MODULE_LOCATOR,
                token,
                flagToTest.toUpperCase()
              )
              .map(Submission::isValid)
          )
          .expectNext(true)
          .verifyComplete();
      }
    }

    private String token;

    private String moduleId;

    @ParameterizedTest
    @MethodSource(
      "org.owasp.herder.test.util.TestConstants#validStaticFlagProvider"
    )
    @DisplayName("if invalid should be rejected")
    void canRejectInvalidStaticFlag(final String invalidStaticFlag) {
      StepVerifier
        .create(
          integrationTestUtils
            .submitFlagApiAndReturnSubmission(
              TestConstants.TEST_MODULE_LOCATOR,
              token,
              invalidStaticFlag
            )
            .map(Submission::isValid)
        )
        .expectNext(false)
        .verifyComplete();
    }

    @Test
    @DisplayName("should return HTTP 401 when not logged in")
    void canRejectUnauthorizedEmptyStaticFlag() {
      integrationTestUtils
        .submitFlagApi(TestConstants.TEST_MODULE_LOCATOR, null, "")
        .expectStatus()
        .isUnauthorized();
    }

    @Test
    @DisplayName("when empty should return HTTP 401 when not logged in")
    void canRejectUnauthorizedStaticFlag() {
      integrationTestUtils
        .submitFlagApi(
          TestConstants.TEST_MODULE_LOCATOR,
          null,
          TestConstants.TEST_STATIC_FLAG
        )
        .expectStatus()
        .isUnauthorized();
    }

    @BeforeEach
    void setup() {
      integrationTestUtils.createTestUser();
      token =
        integrationTestUtils.performAPILoginWithToken(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        );

      moduleId = integrationTestUtils.createStaticTestModule();
    }
  }

  @Autowired
  UserService userService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  ModuleController moduleController;

  @Autowired
  PasswordEncoder passwordEncoder;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  private String userId;

  private String token;

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    // Bypass all rate limiters
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);
  }
}
