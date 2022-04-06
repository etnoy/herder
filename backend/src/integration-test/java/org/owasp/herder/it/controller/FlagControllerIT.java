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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.flag.FlagController;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreAdjustmentService;
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
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("FlagController integration tests")
class FlagControllerIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired ModuleService moduleService;

  @Autowired UserService userService;

  @Autowired SubmissionService submissionService;

  @Autowired ScoreAdjustmentService scoreAdjustmentService;

  @Autowired ScoreboardService scoreboardService;

  @Autowired FlagController flagController;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired FlagHandler flagHandler;

  @Autowired ConfigurationService configurationService;

  @Autowired KeyService keyService;

  @Autowired CryptoService cryptoService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @MockBean FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean InvalidFlagRateLimiter invalidFlagRateLimiter;

  @MockBean ControllerAuthentication controllerAuthentication;

  @Test
  @WithMockUser
  @DisplayName("Can refresh module lists after successful flag submission")
  void canRefreshModuleListsAfterFlagSubmission() {
    integrationTestUtils.createStaticTestModule();

    final String userId = integrationTestUtils.createTestUser();

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(userId));

    flagController
        .submitFlag(TestConstants.TEST_MODULE_LOCATOR, TestConstants.TEST_STATIC_FLAG)
        .block();

    StepVerifier.create(moduleService.findAllModuleLists())
        .assertNext(moduleList -> assertThat(moduleList.getModules().get(0).getIsSolved()).isTrue())
        .verifyComplete();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
  }
}
