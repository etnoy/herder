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
package org.owasp.herder.it.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("SubmissionService integration tests")
class SubmissionServiceIT extends BaseIT {

  @Autowired
  SubmissionService submissionService;

  @Autowired
  UserService userService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  ModuleRepository moduleRepository;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  TeamService teamService;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  Clock clock;

  String userId;

  String moduleId;

  @Test
  @DisplayName("Valid submission of a static flag should be accepted")
  void canAcceptValidStaticFlagSubmission() {
    userId = integrationTestUtils.createTestUser();
    moduleId = integrationTestUtils.createStaticTestModule();
    StepVerifier
      .create(submissionService.submitFlag(userId, moduleId, TestConstants.TEST_STATIC_FLAG))
      .assertNext(submission -> {
        assertThat(submission.getUserId()).isEqualTo(userId);
        assertThat(submission.getModuleId()).isEqualTo(moduleId);
        assertThat(submission.getTeamId()).isNull();
        assertThat(submission.isValid()).isTrue();
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

    Clock testClock = TestConstants.YEAR_2000_CLOCK;

    setClock(testClock);
    integrationTestUtils.submitValidFlag(userId1, moduleId);

    testClock = Clock.offset(testClock, Duration.ofSeconds(1));
    setClock(testClock);

    integrationTestUtils.submitValidFlag(userId2, moduleId);
    testClock = Clock.offset(testClock, Duration.ofSeconds(1));
    setClock(testClock);

    integrationTestUtils.submitValidFlag(userId3, moduleId);

    userService.addUserToTeam(userId1, teamId).block();
    teamService.addMember(teamId, userService.getById(userId1).block()).block();
    submissionService.setTeamIdOfUserSubmissions(userId1, teamId).block();

    userService.addUserToTeam(userId3, teamId).block();
    teamService.addMember(teamId, userService.getById(userId1).block()).block();
    submissionService.setTeamIdOfUserSubmissions(userId3, teamId).block();

    submissionService.refreshSubmissionRanks().block();

    StepVerifier
      .create(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(rankedSubmissions -> {
        assertThat(rankedSubmissions).extracting("id").containsExactly(teamId, userId2);
        assertThat(rankedSubmissions).extracting("principalType").containsExactly(SolverType.TEAM, SolverType.USER);
        assertThat(rankedSubmissions).extracting("rank").containsExactly(1L, 2L);
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

    submissionService.refreshSubmissionRanks().block();

    StepVerifier
      .create(submissionService.findAllRankedByUserId(userId))
      .assertNext(submission -> {
        assertThat(submission.getRank()).isEqualTo(1);
        assertThat(submission.getId()).isEqualTo(user.getId());
        assertThat(submission.getModuleLocator()).isEqualTo(module.getLocator());
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Duplicate submission of a static flag should throw an exception")
  void canRejectDuplicateSubmissionsOfValidStaticFlags() {
    userId = integrationTestUtils.createTestUser();
    moduleId = integrationTestUtils.createStaticTestModule();
    StepVerifier
      .create(
        submissionService
          .submitFlag(userId, moduleId, TestConstants.TEST_STATIC_FLAG)
          .repeat(1)
          .map(Submission::isValid)
      )
      .expectNext(true)
      .expectError(ModuleAlreadySolvedException.class)
      .verify();
  }

  @Test
  @DisplayName("Can set team id of submissions when user joins a team")
  void canSetTeamIdOfSubmissionsWhenJoiningTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String teamId = integrationTestUtils.createTestTeam();

    integrationTestUtils.submitInvalidFlag(userId, moduleId);
    integrationTestUtils.submitValidFlag(userId, moduleId);

    userService.addUserToTeam(userId, teamId).block();

    final UserEntity user = userService.getById(userId).block();
    teamService.addMember(teamId, user).block();

    submissionService.setTeamIdOfUserSubmissions(userId, teamId).block();

    StepVerifier
      .create(submissionService.findAllSubmissions())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(submissions -> assertThat(submissions).extracting(Submission::getTeamId).containsOnly(teamId)
      )
      .verifyComplete();
  }

  @Test
  @DisplayName("Can clear team id of submissions when user leaves a team")
  void canClearTeamIdOfSubmissionsWhenJoiningTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String teamId = integrationTestUtils.createTestTeam();

    integrationTestUtils.submitInvalidFlag(userId, moduleId);
    integrationTestUtils.submitValidFlag(userId, moduleId);

    userService.addUserToTeam(userId, teamId).block();

    final UserEntity user = userService.getById(userId).block();
    teamService.addMember(teamId, user).block();

    submissionService.clearTeamIdOfUserSubmissions(userId).block();

    StepVerifier
      .create(submissionService.findAllSubmissions())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(submissions -> assertThat(submissions).extracting(Submission::getTeamId).containsOnlyNulls())
      .verifyComplete();
  }

  private void resetClock() {
    setClock(Clock.systemDefaultZone());
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();
    resetClock();
  }
}
