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
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("Flag submission integration tests")
class FlagSubmissionIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired SubmissionService submissionService;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired CorrectionRepository correctionRepository;

  @Autowired UserRepository userRepository;

  @Autowired FlagHandler flagHandler;

  @Autowired IntegrationTestUtils integrationTestUtils;

  private String userId;

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    userId = integrationTestUtils.createTestUser();
    integrationTestUtils.createStaticTestModule();
  }

  @Test
  @DisplayName("Duplicate submission of a static flag should throw an exception")
  void canRejectDuplicateSubmissionsOfValidStaticFlags() {
    StepVerifier.create(
            submissionService
                .submit(userId, TestConstants.TEST_MODULE_NAME, TestConstants.TEST_STATIC_FLAG)
                .repeat(1)
                .map(Submission::isValid))
        .expectNext(true)
        .expectError(ModuleAlreadySolvedException.class)
        .verify();
  }

  @Test
  @DisplayName("Valid submission of a static flag should be accepted")
  void canAcceptValidStaticFlagSubmission() {
    StepVerifier.create(
            submissionService
                .submit(userId, TestConstants.TEST_MODULE_NAME, TestConstants.TEST_STATIC_FLAG)
                .map(Submission::isValid))
        .expectNext(true)
        .expectComplete()
        .verify();
  }
}
