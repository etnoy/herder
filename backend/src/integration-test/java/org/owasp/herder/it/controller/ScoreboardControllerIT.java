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
package org.owasp.herder.it.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.ModuleNotFoundException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreAdjustmentService;
import org.owasp.herder.scoring.ScoreboardController;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

@DisplayName("ScoreboardController integration tests")
class ScoreboardControllerIT extends BaseIT {
  @Autowired
  ModuleService moduleService;

  @Autowired
  UserService userService;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  ScoreAdjustmentService scoreAdjustmentService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  ScoreboardController scoreboardController;

  @Autowired
  ModuleRepository moduleRepository;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  ConfigurationService configurationService;

  @Autowired
  KeyService keyService;

  @Autowired
  CryptoService cryptoService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Test
  @WithMockUser
  @DisplayName("Can return error if invalid module locator is given")
  void canReturnErrorForInvalidModuleLocator() {
    StepVerifier
      .create(scoreboardController.getSubmissionsByModuleLocator("XYZ"))
      .expectError(ResponseStatusException.class)
      .verify();
  }

  @Test
  @WithMockUser
  @DisplayName("Can return error for invalid user id")
  void canReturnErrorForInvalidUserId() {
    StepVerifier
      .create(scoreboardController.getSubmissionsByUserId("xyz"))
      .expectError(ResponseStatusException.class)
      .verify();
  }

  @Test
  @WithMockUser
  @DisplayName("Can return error if nonexistent module locator is given")
  void canReturnErrorForNonExistentModuleLocator() {
    StepVerifier
      .create(
        scoreboardController.getSubmissionsByModuleLocator("non-existent")
      )
      .expectError(ModuleNotFoundException.class)
      .verify();
  }

  @Test
  @WithMockUser
  @DisplayName("Can return error for nonexistent user id")
  void canReturnErrorForNonExistentUserId() {
    StepVerifier
      .create(
        scoreboardController.getSubmissionsByUserId(TestConstants.TEST_USER_ID)
      )
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @WithMockUser
  @DisplayName("Can return zero submissions for module without submissions")
  void canReturnZeroSubmissionsForModuleWithoutSubmissions() {
    integrationTestUtils.createStaticTestModule();
    StepVerifier
      .create(
        scoreboardController.getSubmissionsByModuleLocator(
          TestConstants.TEST_MODULE_LOCATOR
        )
      )
      .verifyComplete();
  }

  @Test
  @WithMockUser
  @DisplayName("Can return zero submissions for user without submissions")
  void canReturnZeroSubmissionsForUserWithoutSubmissions() {
    final String userId = integrationTestUtils.createTestUser();
    StepVerifier
      .create(scoreboardController.getSubmissionsByUserId(userId))
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);
  }
}
