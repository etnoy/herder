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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("SubmissionService integration tests")
class SubmissionServiceIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired SubmissionService submissionService;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired RefresherService refresherService;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired UserRepository userRepository;

  @Autowired FlagHandler flagHandler;

  @Autowired IntegrationTestUtils integrationTestUtils;

  private String userId;

  private String moduleId;

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
  }

  @Test
  @DisplayName("Duplicate submission of a static flag should throw an exception")
  void canRejectDuplicateSubmissionsOfValidStaticFlags() {
    userId = integrationTestUtils.createTestUser();
    moduleId = integrationTestUtils.createStaticTestModule();
    StepVerifier.create(
            submissionService
                .submitFlag(userId, moduleId, TestConstants.TEST_STATIC_FLAG)
                .repeat(1)
                .map(Submission::isValid))
        .expectNext(true)
        .expectError(ModuleAlreadySolvedException.class)
        .verify();
  }

  @Test
  @DisplayName("Valid submission of a static flag should be accepted")
  void canAcceptValidStaticFlagSubmission() {
    userId = integrationTestUtils.createTestUser();
    moduleId = integrationTestUtils.createStaticTestModule();
    StepVerifier.create(
            submissionService.submitFlag(userId, moduleId, TestConstants.TEST_STATIC_FLAG))
        .assertNext(
            submission -> {
              assertThat(submission.getUserId()).isEqualTo(userId);
              assertThat(submission.getModuleId()).isEqualTo(moduleId);
              assertThat(submission.getTeamId()).isNull();
              assertThat(submission.isValid()).isTrue();
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find a single ranked submission")
  void canFindASingleRankedSubmission() {
    userId = integrationTestUtils.createTestUser();
    moduleId = integrationTestUtils.createStaticTestModule();
    integrationTestUtils.submitValidFlag(userId, moduleId);

    final UserEntity user = userService.getById(userId).block();
    final ModuleEntity module = moduleService.getById(moduleId).block();

    refresherService.refreshSubmissionRanks().block();

    StepVerifier.create(submissionService.findAllRankedByUserId(userId))
        .assertNext(
            submission -> {
              assertThat(submission.getRank()).isEqualTo(1);
              assertThat(submission.getId()).isEqualTo(user.getId());
              assertThat(submission.getModuleLocator()).isEqualTo(module.getLocator());
            })
        .verifyComplete();
  }

  @Test
  @DisplayName("Can combine submissions for users in a team")
  void canCombineSubmissionsForTeam() {
    final String userId1 = userService.create("User 1").block();
    final String userId2 = userService.create("User 2").block();
    final String userId3 = userService.create("User 3").block();

    final String teamId = integrationTestUtils.createTestTeam();
    final String moduleId = integrationTestUtils.createStaticTestModule();

    Clock testClock = TestConstants.year2000Clock;

    submissionService.setClock(testClock);
    integrationTestUtils.submitValidFlag(userId1, moduleId);

    testClock = Clock.offset(testClock, Duration.ofSeconds(1));
    submissionService.setClock(testClock);

    integrationTestUtils.submitValidFlag(userId2, moduleId);
    testClock = Clock.offset(testClock, Duration.ofSeconds(1));
    submissionService.setClock(testClock);

    integrationTestUtils.submitValidFlag(userId3, moduleId);

    userService.addUserToTeam(userId1, teamId).block();
    userService.addUserToTeam(userId3, teamId).block();

    refresherService.afterUserUpdate(userId1).block();
    refresherService.afterUserUpdate(userId3).block();

    final TeamEntity team = userService.getTeamById(teamId).block();

    refresherService.refreshSubmissionRanks().block();

    StepVerifier.create(submissionService.findAllRankedByTeamId(teamId))
        .assertNext(
            rankedSubmission -> {
              assertThat(rankedSubmission.getId()).isEqualTo(team.getId());
              assertThat(rankedSubmission.getPrincipalType()).isEqualTo(PrincipalType.TEAM);
              assertThat(rankedSubmission.getRank()).isEqualTo(1L);
            })
        .verifyComplete();
  }
}
