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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreAdjustmentService;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("ScoreboardService integration tests")
class ScoreboardServiceIT extends BaseIT {

  @Nested
  @DisplayName("Can get scoreboard")
  class canGetScoreboard {

    String userId1;
    String userId2;
    String userId3;
    String userId4;

    String moduleId1;
    String moduleId2;

    UserEntity user1;
    UserEntity user2;
    UserEntity user3;

    ModuleEntity module1;
    Clock testClock;

    private void addUsers1and3ToTeam(final String teamId) {
      userService.addUserToTeam(userId1, teamId).block();
      user1 = user1.withTeamId(teamId);
      teamService.addMember(teamId, user1).block();
      submissionService.setTeamIdOfUserSubmissions(userId1, teamId).block();

      userService.addUserToTeam(userId3, teamId).block();
      user3 = user3.withTeamId(teamId);
      teamService.addMember(teamId, user3).block();
      submissionService.setTeamIdOfUserSubmissions(userId3, teamId).block();
    }

    private void removeUsers1and3FromTeam(final String teamId) {
      userService.clearTeamForUser(userId1).block();
      user1 = user1.withTeamId(null);
      teamService.expel(teamId, userId1).block();
      submissionService.clearTeamIdOfUserSubmissions(userId1).block();

      userService.clearTeamForUser(userId3).block();
      user3 = user3.withTeamId(null);
      teamService.expel(teamId, userId3).block();
      submissionService.clearTeamIdOfUserSubmissions(userId3).block();
    }

    @Test
    @DisplayName("and break ties using medals")
    void canBreakTiesUsingMedals() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId1, userId2, userId3, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 4L);
          assertThat(scoreboard).extracting("score").containsExactly(100L, 100L, 100L, 0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(1L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 1L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with no strictly positive scores")
    void canGetScoreboardWithNoStrictlyPositiveScores() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      scoreAdjustmentService.submitUserAdjustment(userId1, -1000, "Penalty for cheating").block();
      scoreAdjustmentService.submitUserAdjustment(userId2, -1000, "Penalty for cheating").block();
      scoreAdjustmentService.submitUserAdjustment(userId3, -1000, "Penalty for cheating").block();

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId4, userId1, userId2, userId3);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 4L);
          assertThat(scoreboard).extracting("score").containsExactly(0L, -900L, -900L, -900L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(0L, 1L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 0L, 1L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L, 1L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with a team and user tied for score")
    void canGetScoreboardWithTeamAndUserTied() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();

      addUsers1and3ToTeam(teamId);

      scoreAdjustmentService.submitTeamAdjustment(teamId, -100, "No scores for you!").block();

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, teamId, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L);
          assertThat(scoreboard).extracting("score").containsExactly(100L, 0L, 0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(0L, 1L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with teams with score adjustment remaining after user leaves team")
    void canGetScoreboardWithTeamScoreAdjustmentRemainingAfterUserLeavingTeam() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();

      addUsers1and3ToTeam(teamId);

      scoreAdjustmentService.submitTeamAdjustment(teamId, -1000, "No scores for you!").block();

      removeUsers1and3FromTeam(teamId);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, userId4, userId1, userId3);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 4L);
          assertThat(scoreboard).extracting("score").containsExactly(100L, 0L, -900L, -900L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(0L, 0L, 1L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(1L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L, 1L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with teams with score adjustments")
    void canGetScoreboardWithTeamScoreAdjustments() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();

      addUsers1and3ToTeam(teamId);

      scoreAdjustmentService.submitTeamAdjustment(teamId, -1000, "No scores for you!").block();

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, userId4, teamId);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L);
          assertThat(scoreboard).extracting("score").containsExactly(100L, 0L, -900L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(0L, 0L, 1L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with score adjustments")
    void canGetScoreboardWithUserScoreAdjustments() {
      moduleService.setBaseScore(moduleId1, 100).block();

      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      scoreAdjustmentService.submitUserAdjustment(userId1, -1000, "Penalty for cheating").block();
      scoreAdjustmentService.submitUserAdjustment(userId3, 1000, "Thanks for the bribe").block();

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId3, userId2, userId4, userId1);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 4L);
          assertThat(scoreboard).extracting("score").containsExactly(1100L, 100L, 0L, -900L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(0L, 0L, 0L, 1L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(1L, 0L, 0L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with zero score submissions")
    void canGetScoreboardWithZeroScoreSubmissions() {
      integrationTestUtils.submitValidFlag(userId1, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId1, userId2, userId3, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 4L);
          assertThat(scoreboard).extracting("score").containsExactly(0L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(1L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 1L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("before any submissions are made")
    void canGetScoresWithoutSubmissions() {
      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId1, userId2, userId3, userId4);
          assertThat(scoreboard).extracting("rank").containsOnly(1L);
          assertThat(scoreboard).extracting("score").containsOnly(0L);
          assertThat(scoreboard).extracting("goldMedals").containsOnly(0L);
          assertThat(scoreboard).extracting("silverMedals").containsOnly(0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsOnly(0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with a single submission")
    void canGetScoresWithSingleSubmissions() {
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, userId1, userId3, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 2L, 2L);
          assertThat(scoreboard).extracting("score").containsOnly(0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(1L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsOnly(0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsOnly(0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with team and user submissions")
    void canGetScoresWithUserAndTeamSubmissions() {
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();
      addUsers1and3ToTeam(teamId);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, teamId, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L);
          assertThat(scoreboard).extracting("score").containsExactly(0L, 0L, 0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(1L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 1L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users that have left a team")
    void canGetScoresWithUserThatLeftTeam() {
      final String teamId = integrationTestUtils.createTestTeam();
      addUsers1and3ToTeam(teamId);

      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      tickTestClock();
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      removeUsers1and3FromTeam(teamId);

      refreshScores();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .recordWith(ArrayList::new)
        .thenConsumeWhile(x -> true)
        .consumeRecordedWith(scoreboard -> {
          assertThat(scoreboard).extracting("principalId").containsExactly(userId2, userId3, userId1, userId4);
          assertThat(scoreboard).extracting("rank").containsExactly(1L, 2L, 3L, 3L);
          assertThat(scoreboard).extracting("score").containsExactly(0L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("goldMedals").containsExactly(1L, 0L, 0L, 0L);
          assertThat(scoreboard).extracting("silverMedals").containsExactly(0L, 1L, 0L, 0L);
          assertThat(scoreboard).extracting("bronzeMedals").containsExactly(0L, 0L, 0L, 0L);
        })
        .verifyComplete();
    }

    @BeforeEach
    void setup() {
      userId1 = userService.create("User 1").block();
      userId2 = userService.create("User 2").block();
      userId3 = userService.create("User 3").block();
      userId4 = userService.create("User 4").block();

      moduleId1 = integrationTestUtils.createStaticTestModule();
      moduleId2 = moduleService.create("Test 2", "test-2").block();

      user1 = userService.getById(userId1).block();
      user2 = userService.getById(userId2).block();
      user3 = userService.getById(userId3).block();
      module1 = moduleService.getById(moduleId1).block();
      module1 = moduleService.getById(moduleId2).block();
      setInitialClock();
    }
  }

  @Autowired
  SubmissionService submissionService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  UserService userService;

  @Autowired
  TeamService teamService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  ScoreAdjustmentService scoreAdjustmentService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  @MockBean
  Clock clock;

  Clock testClock;

  private void resetClock() {
    setClock(Clock.systemDefaultZone());
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  private void tickTestClock() {
    testClock = Clock.offset(testClock, Duration.ofSeconds(1));
    setClock(testClock);
  }

  private void setInitialClock() {
    testClock = TestConstants.year2000Clock;
    setClock(testClock);
  }

  private void refreshScores() {
    submissionService.refreshSubmissionRanks().block();
    scoreboardService.refreshScoreboard().block();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);

    resetClock();
  }
}
