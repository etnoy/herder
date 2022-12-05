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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.scoring.ScoreAdjustmentService;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.RefresherService;
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

    @Test
    @DisplayName("and break ties using medals")
    void canBreakTiesUsingMedals() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isOne();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getRank()).isEqualTo(4L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with no strictly positive scores")
    void canGetScoreboardWithNoStrictlyPositiveScores() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      scoreAdjustmentService
        .submitUserAdjustment(userId1, -1000, "Penalty for cheating")
        .block();
      scoreAdjustmentService
        .submitUserAdjustment(userId2, -1000, "Penalty for cheating")
        .block();
      scoreAdjustmentService
        .submitUserAdjustment(userId3, -1000, "Penalty for cheating")
        .block();

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(4L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isOne();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with a team and user tied for score")
    void canGetScoreboardWithTeamAndUserTied() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();

      scoreAdjustmentService
        .submitTeamAdjustment(teamId, -100, "No scores for you!")
        .block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();

      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(teamId);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.TEAM);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(0L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName(
      "with teams with score adjustment remaining after user leaves team"
    )
    void canGetScoreboardWithTeamScoreAdjustmentRemainingAfterUserLeavingTeam() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();

      scoreAdjustmentService
        .submitTeamAdjustment(teamId, -1000, "No scores for you!")
        .block();

      userService.clearTeamForUser(userId1).block();
      userService.clearTeamForUser(userId3).block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();

      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(4L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isOne();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with teams with score adjustments")
    void canGetScoreboardWithTeamScoreAdjustments() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();

      scoreAdjustmentService
        .submitTeamAdjustment(teamId, -1000, "No scores for you!")
        .block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();

      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(teamId);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.TEAM);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with score adjustments")
    void canGetScoreboardWithUserScoreAdjustments() {
      moduleService.setBaseScore(moduleId1, 100).block();

      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      scoreAdjustmentService
        .submitUserAdjustment(userId1, -1000, "Penalty for cheating")
        .block();
      scoreAdjustmentService
        .submitUserAdjustment(userId3, 1000, "Thanks for the bribe")
        .block();

      refresherService.refreshSubmissionRanks().block();

      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(1100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isOne();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(100L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(4L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(-900L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users with zero score submissions")
    void canGetScoreboardWithZeroScoreSubmissions() {
      Clock testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(1L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(0L);
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(0L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isEqualTo(0L);
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isOne();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(4L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("before any submissions are made")
    void canGetScoresWithoutSubmissions() {
      refresherService.refreshSubmissionRanks().block();

      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with a single submission")
    void canGetScoresWithSingleSubmissions() {
      integrationTestUtils.submitValidFlag(userId2, moduleId1);

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with team and user submissions")
    void canGetScoresWithUserAndTeamSubmissions() {
      Clock testClock = TestConstants.year2000Clock;

      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      setClock(testClock);

      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(teamId);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.TEAM);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("with users that have left a team")
    void canGetScoresWithUserThatLeftTeam() {
      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      Clock testClock = TestConstants.year2000Clock;

      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId1);
      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      setClock(testClock);

      integrationTestUtils.submitValidFlag(userId3, moduleId1);

      userService.clearTeamForUser(userId1).block();
      userService.clearTeamForUser(userId3).block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();
      refresherService.refreshScoreboard().block();

      StepVerifier
        .create(scoreboardService.getScoreboard())
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId2);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isOne();
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isOne();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId3);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(2L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isOne();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId1);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
        })
        .assertNext(scoreboardEntry -> {
          assertThat(scoreboardEntry.getPrincipalId()).isEqualTo(userId4);
          assertThat(scoreboardEntry.getPrincipalType())
            .isEqualTo(PrincipalType.USER);
          assertThat(scoreboardEntry.getRank()).isEqualTo(3L);
          assertThat(scoreboardEntry.getScore()).isZero();
          assertThat(scoreboardEntry.getGoldMedals()).isZero();
          assertThat(scoreboardEntry.getSilverMedals()).isZero();
          assertThat(scoreboardEntry.getBronzeMedals()).isZero();
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
    }
  }

  @Autowired
  RefresherService refresherService;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  UserService userService;

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

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class)))
      .thenReturn(mockBucket);

    resetClock();
  }
}
