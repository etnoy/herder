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
package org.owasp.herder.it.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("RankedSubmissionRepository integration tests")
class RankedSubmissionRepositoryIT extends BaseIT {

  @Nested
  @DisplayName("Can get unranked scoreboard")
  class canGetUnrankedScoreboard {

    String userId1;

    String userId2;
    String userId3;
    String moduleId1;

    String moduleId2;
    UserEntity user1;

    UserEntity user2;
    UserEntity user3;
    ModuleEntity module1;

    ModuleEntity module2;
    Clock testClock;

    @Test
    @DisplayName("before any submissions are made")
    void canGetEmptyScoreListBeforeAnySubmissions() {
      StepVerifier
        .create(rankedSubmissionRepository.getUnrankedScoreboard())
        .verifyComplete();
    }

    @Test
    @DisplayName("for user and team submissions")
    void canGetScoresForUserAndTeamSubmissions() {
      Clock testClock = TestConstants.year2000Clock;

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId2)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId1)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId1)
            .moduleId(moduleId2)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId3)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      final String teamId = integrationTestUtils.createTestTeam();
      userService.addUserToTeam(userId1, teamId).block();
      userService.addUserToTeam(userId3, teamId).block();
      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId3).block();

      refresherService.refreshSubmissionRanks().block();

      StepVerifier
        .create(rankedSubmissionRepository.getUnrankedScoreboard())
        .recordWith(HashSet::new)
        .expectNextCount(2)
        .consumeRecordedWith(resultSet -> {
          assertThat(resultSet)
            .filteredOn(rankedSubmission -> rankedSubmission.getTeam() != null)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getTeam().getId().equals(teamId)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getScore().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getGoldMedals().equals(1L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getSilverMedals().equals(1L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getBronzeMedals().equals(0L)
            )
            .hasSize(1);

          assertThat(resultSet)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getUser().getId().equals(userId2)
            )
            .filteredOn(rankedSubmission -> rankedSubmission.getTeam() == null)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getScore().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getGoldMedals().equals(1L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getSilverMedals().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getBronzeMedals().equals(0L)
            )
            .hasSize(1);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("for user submissions")
    void canGetScoresForUserSubmissions() {
      Clock testClock = TestConstants.year2000Clock;

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId2)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId1)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));

      submissionRepository
        .save(
          Submission
            .builder()
            .userId(userId3)
            .moduleId(moduleId1)
            .isValid(true)
            .time(LocalDateTime.now(testClock))
            .flag("abc")
            .build()
        )
        .block();

      refresherService.refreshSubmissionRanks().block();

      StepVerifier
        .create(rankedSubmissionRepository.getUnrankedScoreboard())
        .recordWith(HashSet::new)
        .expectNextCount(3)
        .consumeRecordedWith(resultSet -> {
          assertThat(resultSet)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getUser().getId().equals(userId1)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getScore().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getGoldMedals().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getSilverMedals().equals(1L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getBronzeMedals().equals(0L)
            )
            .hasSize(1);

          assertThat(resultSet)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getUser().getId().equals(userId2)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getScore().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getGoldMedals().equals(1L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getSilverMedals().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getBronzeMedals().equals(0L)
            )
            .hasSize(1);

          assertThat(resultSet)
            .filteredOn(rankedSubmission ->
              rankedSubmission.getUser().getId().equals(userId3)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getScore().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getGoldMedals().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getSilverMedals().equals(0L)
            )
            .filteredOn(rankedSubmission ->
              rankedSubmission.getBronzeMedals().equals(1L)
            )
            .hasSize(1);
        })
        .verifyComplete();
    }

    @BeforeEach
    void setup() {
      userId1 = userService.create("User 1").block();
      userId2 = userService.create("User 2").block();
      userId3 = userService.create("User 3").block();

      moduleId1 = integrationTestUtils.createStaticTestModule();
      moduleId2 = moduleService.create("test 2", "test-2").block();

      user1 = userService.getById(userId1).block();
      user2 = userService.getById(userId2).block();
      user3 = userService.getById(userId3).block();

      module1 = moduleService.getById(moduleId1).block();
      module2 = moduleService.getById(moduleId2).block();
    }
  }

  @Nested
  @DisplayName("Can rank submissions")
  class canRankSubmissions {

    @Nested
    @DisplayName("for a team")
    class forTeam {

      String teamId;

      @Test
      @DisplayName("per team")
      void canCombineSubmissionsForTeam() {
        StepVerifier
          .create(
            rankedSubmissionRepository
              .findAllByTeamId(teamId)
              .map(RankedSubmission::getTeam)
              .map(TeamEntity::getId)
          )
          .expectNext(teamId)
          .verifyComplete();
      }

      @Test
      @DisplayName("per module")
      void canCombineSubmissionsForTeamPerModule() {
        StepVerifier
          .create(
            rankedSubmissionRepository
              .findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR)
              .map(RankedSubmission::getTeam)
              .map(TeamEntity::getId)
          )
          .expectNext(teamId)
          .verifyComplete();
      }

      @BeforeEach
      void setup() {
        teamId = integrationTestUtils.createTestTeam();

        userService.addUserToTeam(userId1, teamId).block();
        userService.addUserToTeam(userId2, teamId).block();
        userService.addUserToTeam(userId3, teamId).block();

        refresherService.afterUserUpdate(userId1).block();
        refresherService.afterUserUpdate(userId2).block();
        refresherService.afterUserUpdate(userId3).block();

        refresherService.refreshSubmissionRanks().block();
      }
    }

    @Nested
    @DisplayName("for a team and a user")
    class forTeamAndUser {

      String teamId;

      @Test
      @DisplayName("per module")
      void canCombineSubmissionsForTeamPerModule() {
        StepVerifier
          .create(
            rankedSubmissionRepository.findAllByModuleLocator(
              TestConstants.TEST_MODULE_LOCATOR
            )
          )
          .assertNext(submission ->
            assertThat(submission.getTeam().getId()).isEqualTo(teamId)
          )
          .assertNext(submission ->
            assertThat(submission.getUser().getId()).isEqualTo(userId2)
          )
          .verifyComplete();
      }

      @Test
      @DisplayName("per team")
      void canCombineSubmissionsPerTeam() {
        StepVerifier
          .create(
            rankedSubmissionRepository
              .findAllByTeamId(teamId)
              .map(RankedSubmission::getRank)
          )
          .expectNext(1L)
          .verifyComplete();
      }

      @Test
      @DisplayName("per user")
      void canCombineSubmissionsPerUser() {
        StepVerifier
          .create(
            rankedSubmissionRepository
              .findAllByUserId(userId2)
              .map(RankedSubmission::getRank)
          )
          .expectNext(2L)
          .verifyComplete();
      }

      @BeforeEach
      void setup() {
        teamId = integrationTestUtils.createTestTeam();

        userService.addUserToTeam(userId1, teamId).block();
        userService.addUserToTeam(userId3, teamId).block();

        refresherService.afterUserUpdate(userId1).block();
        refresherService.afterUserUpdate(userId3).block();

        refresherService.refreshSubmissionRanks().block();
      }
    }

    @Nested
    @DisplayName("for a team and a user with multiple modules")
    class forTeamAndUserWithMultipleModules {

      String teamId;
      String moduleId2;

      @Test
      @DisplayName("per team")
      void canCombineSubmissionsForTeam() {
        StepVerifier
          .create(rankedSubmissionRepository.findAllByTeamId(teamId))
          .recordWith(HashSet::new)
          .expectNextCount(2)
          .consumeRecordedWith(resultSet -> {
            assertThat(resultSet)
              .filteredOn(rankedSubmission ->
                rankedSubmission
                  .getModule()
                  .getLocator()
                  .equals(TestConstants.TEST_MODULE_LOCATOR) &&
                rankedSubmission.getRank().equals(1L)
              )
              .hasSize(1);
            assertThat(resultSet)
              .filteredOn(rankedSubmission ->
                rankedSubmission.getModule().getLocator().equals("test-2") &&
                rankedSubmission.getRank().equals(2L)
              )
              .hasSize(1);
          })
          .verifyComplete();
      }

      @Test
      @DisplayName("for first module")
      void canCombineSubmissionsForTeamForFirstModule() {
        StepVerifier
          .create(
            rankedSubmissionRepository.findAllByModuleLocator(
              TestConstants.TEST_MODULE_LOCATOR
            )
          )
          .assertNext(submission ->
            assertThat(submission.getTeam().getId()).isEqualTo(teamId)
          )
          .assertNext(submission ->
            assertThat(submission.getUser().getId()).isEqualTo(userId2)
          )
          .verifyComplete();
      }

      @Test
      @DisplayName("for second module")
      void canCombineSubmissionsForTeamForSecondModule() {
        StepVerifier
          .create(rankedSubmissionRepository.findAllByModuleLocator("test-2"))
          .assertNext(submission ->
            assertThat(submission.getUser().getId()).isEqualTo(userId2)
          )
          .assertNext(submission ->
            assertThat(submission.getTeam().getId()).isEqualTo(teamId)
          )
          .verifyComplete();
      }

      @Test
      @DisplayName("for the user")
      void canCombineSubmissionsForUserId2() {
        StepVerifier
          .create(rankedSubmissionRepository.findAllByUserId(userId2))
          .recordWith(HashSet::new)
          .expectNextCount(2)
          .consumeRecordedWith(resultSet -> {
            assertThat(resultSet)
              .filteredOn(rankedSubmission ->
                rankedSubmission
                  .getModule()
                  .getLocator()
                  .equals(TestConstants.TEST_MODULE_LOCATOR) &&
                rankedSubmission.getRank().equals(2L)
              )
              .hasSize(1);
            assertThat(resultSet)
              .filteredOn(rankedSubmission ->
                rankedSubmission.getModule().getLocator().equals("test-2") &&
                rankedSubmission.getRank().equals(1L)
              )
              .hasSize(1);
          })
          .verifyComplete();
      }

      @BeforeEach
      void setup() {
        teamId = integrationTestUtils.createTestTeam();
        moduleId2 = moduleService.create("Test module 2", "test-2").block();
        setClock(testClock);
        testClock = Clock.offset(testClock, Duration.ofSeconds(1));
        setClock(testClock);
        integrationTestUtils.submitValidFlag(userId2, moduleId2);

        testClock = Clock.offset(testClock, Duration.ofSeconds(1));
        setClock(testClock);
        integrationTestUtils.submitValidFlag(userId3, moduleId2);

        testClock = Clock.offset(testClock, Duration.ofSeconds(1));
        setClock(testClock);
        integrationTestUtils.submitValidFlag(userId1, moduleId2);

        userService.addUserToTeam(userId1, teamId).block();
        userService.addUserToTeam(userId3, teamId).block();
        refresherService.afterUserUpdate(userId1).block();
        refresherService.afterUserUpdate(userId3).block();

        refresherService.refreshSubmissionRanks().block();
      }
    }

    String userId1;
    String userId2;
    String userId3;

    String moduleId;

    UserEntity user1;
    UserEntity user2;
    UserEntity user3;

    ModuleEntity module;

    Clock testClock;

    @Test
    @DisplayName("for a module")
    void canRankSubmissionsForModule() {
      StepVerifier
        .create(
          rankedSubmissionRepository.findAllByModuleLocator(
            TestConstants.TEST_MODULE_LOCATOR
          )
        )
        .assertNext(sanitizedRankedSubmission -> {
          assertThat(sanitizedRankedSubmission.getRank()).isEqualTo(1);
          assertThat(sanitizedRankedSubmission.getUser().getId())
            .isEqualTo(user1.getId());
          assertThat(sanitizedRankedSubmission.getModule().getLocator())
            .isEqualTo(module.getLocator());
          assertThat(sanitizedRankedSubmission.getBaseScore()).isEqualTo(500);
          assertThat(sanitizedRankedSubmission.getBonusScore()).isEqualTo(20);
          assertThat(sanitizedRankedSubmission.getScore()).isEqualTo(520);
        })
        .assertNext(sanitizedRankedSubmission -> {
          assertThat(sanitizedRankedSubmission.getRank()).isEqualTo(2);
          assertThat(sanitizedRankedSubmission.getUser().getId())
            .isEqualTo(user2.getId());
          assertThat(sanitizedRankedSubmission.getModule().getLocator())
            .isEqualTo(module.getLocator());
          assertThat(sanitizedRankedSubmission.getBaseScore()).isEqualTo(500);
          assertThat(sanitizedRankedSubmission.getBonusScore()).isEqualTo(10);
          assertThat(sanitizedRankedSubmission.getScore()).isEqualTo(510);
        })
        .assertNext(sanitizedRankedSubmission -> {
          assertThat(sanitizedRankedSubmission.getRank()).isEqualTo(3);
          assertThat(sanitizedRankedSubmission.getUser().getId())
            .isEqualTo(user3.getId());
          assertThat(sanitizedRankedSubmission.getModule().getLocator())
            .isEqualTo(module.getLocator());
          assertThat(sanitizedRankedSubmission.getBaseScore()).isEqualTo(500);
          assertThat(sanitizedRankedSubmission.getBonusScore()).isEqualTo(5);
          assertThat(sanitizedRankedSubmission.getScore()).isEqualTo(505);
        })
        .verifyComplete();
    }

    @Test
    @DisplayName("for users")
    void canRankSubmissionsForUsers() {
      StepVerifier
        .create(
          rankedSubmissionRepository
            .findAllByUserId(userId1)
            .map(RankedSubmission::getRank)
        )
        .expectNext(1L)
        .verifyComplete();

      StepVerifier
        .create(
          rankedSubmissionRepository
            .findAllByUserId(userId2)
            .map(RankedSubmission::getRank)
        )
        .expectNext(2L)
        .verifyComplete();

      StepVerifier
        .create(
          rankedSubmissionRepository
            .findAllByUserId(userId3)
            .map(RankedSubmission::getRank)
        )
        .expectNext(3L)
        .verifyComplete();
    }

    @BeforeEach
    void setup() {
      userId1 = userService.create("User 1").block();
      userId2 = userService.create("User 2").block();
      userId3 = userService.create("User 3").block();

      moduleId = integrationTestUtils.createStaticTestModule();

      moduleService.setBaseScore(moduleId, 500).block();

      final ArrayList<Integer> scores = new ArrayList<>();

      scores.add(20);
      scores.add(10);
      scores.add(5);

      moduleService.setBonusScores(moduleId, scores).block();

      testClock = TestConstants.year2000Clock;
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId1, moduleId);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId2, moduleId);

      testClock = Clock.offset(testClock, Duration.ofSeconds(1));
      setClock(testClock);
      integrationTestUtils.submitValidFlag(userId3, moduleId);

      user1 = userService.getById(userId1).block();
      user2 = userService.getById(userId2).block();
      user3 = userService.getById(userId3).block();
      module = moduleService.getById(moduleId).block();

      refresherService.refreshSubmissionRanks().block();
    }
  }

  @Autowired
  RefresherService refresherService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  UserService userService;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  RankedSubmissionRepository rankedSubmissionRepository;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  @MockBean
  Clock clock;

  @Test
  @DisplayName("Can find a single valid ranked submission")
  void canFindSingleValidRankedSubmission() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();

    moduleService.setBaseScore(moduleId, 1000).block();

    final ArrayList<Integer> scores = new ArrayList<>();

    scores.add(100);
    moduleService.setBonusScores(moduleId, scores).block();

    final UserEntity user = userService.getById(userId).block();
    final ModuleEntity module = moduleService.getById(moduleId).block();

    integrationTestUtils.submitValidFlag(userId, moduleId);

    refresherService.refreshSubmissionRanks().block();

    StepVerifier
      .create(rankedSubmissionRepository.findAll())
      .assertNext(rankedSubmission -> {
        assertThat(rankedSubmission.getRank()).isEqualTo(1);
        assertThat(rankedSubmission.getUser()).isEqualTo(user);
        assertThat(rankedSubmission.getModule()).isEqualTo(module);
        assertThat(rankedSubmission.getBaseScore()).isEqualTo(1000);
        assertThat(rankedSubmission.getBonusScore()).isEqualTo(100);
        assertThat(rankedSubmission.getScore()).isEqualTo(1100);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can rank submissions to a single module")
  void canRankSubmissionsToSingleModule() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId1 = userService.create("Test user 1").block();
    final String userId2 = userService.create("Test user 2").block();
    final String userId3 = userService.create("Test user 3").block();

    moduleService.setBaseScore(moduleId, 500).block();

    final ArrayList<Integer> scores = new ArrayList<>();

    scores.add(20);
    scores.add(10);
    scores.add(5);

    moduleService.setBonusScores(moduleId, scores).block();

    final UserEntity user1 = userService.getById(userId1).block();
    final UserEntity user2 = userService.getById(userId2).block();
    final UserEntity user3 = userService.getById(userId3).block();

    final ModuleEntity module = moduleService.getById(moduleId).block();

    Clock testClock = TestConstants.year2000Clock;

    submissionRepository
      .save(
        Submission
          .builder()
          .userId(userId1)
          .moduleId(moduleId)
          .time(LocalDateTime.now(TestConstants.year2000Clock))
          .isValid(true)
          .time(LocalDateTime.now(testClock))
          .flag("abc")
          .build()
      )
      .block();

    testClock = Clock.offset(testClock, Duration.ofSeconds(1));

    submissionRepository
      .save(
        Submission
          .builder()
          .userId(userId2)
          .moduleId(moduleId)
          .time(LocalDateTime.now(TestConstants.year2000Clock))
          .isValid(true)
          .time(LocalDateTime.now(testClock))
          .flag("abc")
          .build()
      )
      .block();

    testClock = Clock.offset(testClock, Duration.ofSeconds(1));

    submissionRepository
      .save(
        Submission
          .builder()
          .userId(userId3)
          .moduleId(moduleId)
          .time(LocalDateTime.now(TestConstants.year2000Clock))
          .isValid(true)
          .time(LocalDateTime.now(testClock))
          .flag("abc")
          .build()
      )
      .block();

    refresherService.refreshSubmissionRanks().block();

    StepVerifier
      .create(rankedSubmissionRepository.findAll())
      .assertNext(rankedSubmission -> {
        assertThat(rankedSubmission.getRank()).isEqualTo(1);
        assertThat(rankedSubmission.getUser()).isEqualTo(user1);
        assertThat(rankedSubmission.getModule()).isEqualTo(module);
        assertThat(rankedSubmission.getBaseScore()).isEqualTo(500);
        assertThat(rankedSubmission.getBonusScore()).isEqualTo(20);
        assertThat(rankedSubmission.getScore()).isEqualTo(520);
      })
      .assertNext(rankedSubmission -> {
        assertThat(rankedSubmission.getRank()).isEqualTo(2);
        assertThat(rankedSubmission.getUser()).isEqualTo(user2);
        assertThat(rankedSubmission.getModule()).isEqualTo(module);
        assertThat(rankedSubmission.getBaseScore()).isEqualTo(500);
        assertThat(rankedSubmission.getBonusScore()).isEqualTo(10);
        assertThat(rankedSubmission.getScore()).isEqualTo(510);
      })
      .assertNext(rankedSubmission -> {
        assertThat(rankedSubmission.getRank()).isEqualTo(3);
        assertThat(rankedSubmission.getUser()).isEqualTo(user3);
        assertThat(rankedSubmission.getModule()).isEqualTo(module);
        assertThat(rankedSubmission.getBaseScore()).isEqualTo(500);
        assertThat(rankedSubmission.getBonusScore()).isEqualTo(5);
        assertThat(rankedSubmission.getScore()).isEqualTo(505);
      })
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
