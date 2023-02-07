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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.UnrankedScoreboardEntry;
import org.owasp.herder.scoring.UnrankedScoreboardEntry.UnrankedScoreboardEntryBuilder;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.SolverEntity;
import org.owasp.herder.user.SolverEntity.SolverEntityBuilder;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamEntity.TeamEntityBuilder;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserEntity.UserEntityBuilder;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
@DisplayName("RefresherService unit tests")
class ScoreboardServiceTest extends BaseTest {

  @Mock
  SubmissionRepository submissionRepository;

  @Mock
  ScoreboardRepository scoreboardRepository;

  @Mock
  RankedSubmissionRepository rankedSubmissionRepository;

  @Mock
  UserService userService;

  @Mock
  TeamRepository teamRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  ScoreboardService scoreboardService;

  ArgumentCaptor<ArrayList<ScoreboardEntry>> scoreboardCaptor;

  int testUserIndex;

  int testTeamIndex;

  UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder;

  @BeforeEach
  void setup() {
    testUserIndex = 1;
    testTeamIndex = 1;

    unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();

    unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
    unrankedScoreboardEntryBuilder.score(0L);
    unrankedScoreboardEntryBuilder.baseScore(0L);
    unrankedScoreboardEntryBuilder.bonusScore(0L);
    unrankedScoreboardEntryBuilder.goldMedals(0L);
    unrankedScoreboardEntryBuilder.silverMedals(0L);
    unrankedScoreboardEntryBuilder.bronzeMedals(0L);

    scoreboardService = new ScoreboardService(scoreboardRepository, rankedSubmissionRepository, userService);
  }

  @Nested
  @DisplayName("Can throw error when unranked scoreboard is incorrectly sorted")
  class badSort {

    UnrankedScoreboardEntry unrankedScoreboardEntry1;

    UserEntity testUser1;
    UserEntity testUser2;

    @BeforeEach
    void setup() {
      testUser1 = createTestUser();
      testUser2 = createTestUser();

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(100L);
      unrankedScoreboardEntryBuilder.baseScore(100L);

      unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(100L);
      unrankedScoreboardEntryBuilder.baseScore(100L);
    }

    @Test
    @DisplayName("according to score")
    void refreshScoreboard_BadSortByScore_ThrowsException() {
      unrankedScoreboardEntryBuilder.score(200L);
      unrankedScoreboardEntryBuilder.baseScore(200L);

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntryBuilder.build()));

      StepVerifier.create(scoreboardService.refreshScoreboard()).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("according to gold medals")
    void refreshScoreboard_BadSortByGoldMedals_ThrowsException() {
      unrankedScoreboardEntryBuilder.goldMedals(1L);

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntryBuilder.build()));

      StepVerifier.create(scoreboardService.refreshScoreboard()).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("according to silver medals")
    void refreshScoreboard_BadSortBySilverMedals_ThrowsException() {
      unrankedScoreboardEntryBuilder.silverMedals(1L);

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntryBuilder.build()));

      StepVerifier.create(scoreboardService.refreshScoreboard()).expectError(IllegalArgumentException.class).verify();
    }

    @Test
    @DisplayName("according to bronze medals")
    void refreshScoreboard_BadSortByBronzeMedals_ThrowsException() {
      unrankedScoreboardEntryBuilder.bronzeMedals(1L);

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntryBuilder.build()));

      StepVerifier.create(scoreboardService.refreshScoreboard()).expectError(IllegalArgumentException.class).verify();
    }
  }

  @Test
  @DisplayName("Can get scoreboard")
  void getScoreboard_ThreeUsersToScore_CallsRepository() {
    when(scoreboardRepository.findAll()).thenReturn(Flux.empty());

    StepVerifier.create(scoreboardService.getScoreboard()).verifyComplete();

    verify(scoreboardRepository, times(1)).findAll();
  }

  private SolverEntity createSolverEntityFromUser(final UserEntity userEntity) {
    final SolverEntityBuilder principalEntityBuilder = SolverEntity.builder();
    principalEntityBuilder.creationTime(LocalDateTime.MIN);
    principalEntityBuilder.solverType(SolverType.USER);

    principalEntityBuilder.displayName(userEntity.getDisplayName());
    principalEntityBuilder.id(userEntity.getId());
    return principalEntityBuilder.build();
  }

  private SolverEntity createSolverEntityFromTeam(final TeamEntity teamEntity) {
    final SolverEntityBuilder principalEntityBuilder = SolverEntity.builder();
    principalEntityBuilder.creationTime(LocalDateTime.MIN);
    principalEntityBuilder.solverType(SolverType.TEAM);

    principalEntityBuilder.displayName(teamEntity.getDisplayName());
    principalEntityBuilder.id(teamEntity.getId());
    return principalEntityBuilder.build();
  }

  private UserEntity createTestUser() {
    final UserEntityBuilder userEntityBuilder = UserEntity.builder();
    userEntityBuilder.key(TestConstants.TEST_BYTE_ARRAY);
    userEntityBuilder.id("id" + testUserIndex);
    userEntityBuilder.displayName("Test User " + testUserIndex);

    testUserIndex++;
    return userEntityBuilder.build();
  }

  private TeamEntity createTestTeam() {
    final ArrayList<UserEntity> members = new ArrayList<>();
    members.add(createTestUser());
    members.add(createTestUser());

    final TeamEntityBuilder teamEntityBuilder = TeamEntity.builder();
    teamEntityBuilder.id("id" + testTeamIndex);
    teamEntityBuilder.displayName("Test User " + testTeamIndex);
    teamEntityBuilder.members(members);

    testTeamIndex++;
    return teamEntityBuilder.build();
  }

  @Test
  @DisplayName("Can throw error if there are submissions for nonexisting users")
  void refreshScoreboard_SubmissionsForNonExistingUsers_ThrowsError() {
    final UserEntity testUser1 = createTestUser();
    final UserEntity testUser2 = createTestUser();
    final UserEntity testUser3 = createTestUser();

    unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
    unrankedScoreboardEntryBuilder.user(testUser1);
    unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
    unrankedScoreboardEntryBuilder.score(10L);
    unrankedScoreboardEntryBuilder.baseScore(10L);
    unrankedScoreboardEntryBuilder.goldMedals(10L);
    unrankedScoreboardEntryBuilder.silverMedals(10L);
    unrankedScoreboardEntryBuilder.bronzeMedals(10L);
    final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

    unrankedScoreboardEntryBuilder.user(testUser2);
    unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
    final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

    unrankedScoreboardEntryBuilder.user(testUser3);
    unrankedScoreboardEntryBuilder.displayName(testUser3.getDisplayName());
    final UnrankedScoreboardEntry unrankedScoreboardEntry3 = unrankedScoreboardEntryBuilder.build();

    when(rankedSubmissionRepository.getUnrankedScoreboard())
      .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2, unrankedScoreboardEntry3));

    //Only submit two users
    when(userService.findAllSolvers())
      .thenReturn(Flux.just(testUser1, testUser2).map(this::createSolverEntityFromUser));

    StepVerifier.create(scoreboardService.refreshScoreboard()).expectError(IllegalStateException.class).verify();
  }

  @Nested
  class scoreboardRefreshTests {

    @BeforeEach
    void setup() {
      when(scoreboardRepository.deleteAll()).thenReturn(Mono.empty());
      when(scoreboardRepository.saveAll(any(ArrayList.class))).thenAnswer(list -> Flux.empty());

      scoreboardCaptor = ArgumentCaptor.forClass(ArrayList.class);

      when(rankedSubmissionRepository.getUnrankedScoreboard()).thenReturn(Flux.empty());
      when(userService.findAllSolvers()).thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("Can produce an empty scoreboard when no users or submissions are present")
    void refreshScoreboard_NoUsersAndNoSubmissions_CreatesEmptyScoreboard() {
      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

      assertThat(newScoreboard).isEmpty();
    }

    @Test
    @DisplayName("Can produce a scoreboard with users scoring positive, zero, zero-adjusted, and negative scores")
    void refreshScoreboard_PositiveZeroZeroAdjustedAndNegativeScores_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();
      final UserEntity testUser3 = createTestUser();
      final UserEntity testUser4 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(1000L);
      unrankedScoreboardEntryBuilder.baseScore(1000L);

      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(0L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser3);
      unrankedScoreboardEntryBuilder.displayName(testUser3.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(-100L);
      unrankedScoreboardEntryBuilder.goldMedals(100L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry3 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2, unrankedScoreboardEntry3));

      when(userService.findAllSolvers())
        .thenReturn(
          Flux.just(testUser1, testUser2, testUser3, testUser4).map(user -> createSolverEntityFromUser(user))
        );

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L, 2L, 4L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId(), testUser4.getId(), testUser3.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with users scoring positive, zero, and negative scores")
    void refreshScoreboard_PositiveZeroAndNegativeScores_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();
      final UserEntity testUser3 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(1000L);
      unrankedScoreboardEntryBuilder.baseScore(1000L);

      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2, testUser3).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L, 3L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser3.getId(), testUser2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with a zero-score (+ bronze) user followed by a negative score")
    void refreshScoreboard_ZeroScoreBronzeAndNegativeScore_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(0L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      unrankedScoreboardEntryBuilder.bronzeMedals(1L);

      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with a zero-score (+ silver) user followed by a negative score")
    void refreshScoreboard_ZeroScoreSilverAndNegativeScore_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(0L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      unrankedScoreboardEntryBuilder.silverMedals(1L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard if two users have negative scores")
    void refreshScoreboard_TwoUsersWithNegativeScores_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(0L);

      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-500L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));
      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with a zero-score (+ silver and gold) user followed by a negative score")
    void refreshScoreboard_ZeroScoreGoldSilverAndNegativeScore_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(0L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(1L);
      unrankedScoreboardEntryBuilder.silverMedals(1L);

      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(-100L);
      unrankedScoreboardEntryBuilder.baseScore(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with two teams with positive scores")
    void refreshScoreboard_TwoTeamsWithPositiveScores_CreatesScoreboard() {
      final TeamEntity testTeam1 = createTestTeam();
      final TeamEntity testTeam2 = createTestTeam();

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testTeam1, testTeam2).map(team -> createSolverEntityFromTeam(team)));

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);

      unrankedScoreboardEntryBuilder.user(testTeam1.getMembers().get(0));
      unrankedScoreboardEntryBuilder.team(testTeam1);
      unrankedScoreboardEntryBuilder.displayName(testTeam1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(1000L);
      unrankedScoreboardEntryBuilder.baseScore(1000L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(100L);
      unrankedScoreboardEntryBuilder.silverMedals(10L);
      unrankedScoreboardEntryBuilder.bronzeMedals(1L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testTeam2.getMembers().get(0));
      unrankedScoreboardEntryBuilder.team(testTeam2);
      unrankedScoreboardEntryBuilder.displayName(testTeam2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(100L);
      unrankedScoreboardEntryBuilder.baseScore(100L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(50L);
      unrankedScoreboardEntryBuilder.silverMedals(5L);
      unrankedScoreboardEntryBuilder.bronzeMedals(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testTeam1.getId(), testTeam2.getId());
    }

    @Test
    @DisplayName("Can produce a scoreboard with a user and team with identical principal id")
    void refreshScoreboard_TwoPrincipalsWithSameID_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser().withId("collision");
      final TeamEntity testTeam1 = createTestTeam().withId("collision");

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(createSolverEntityFromUser(testUser1), createSolverEntityFromTeam(testTeam1)));

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);

      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(1000L);
      unrankedScoreboardEntryBuilder.baseScore(1000L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(100L);
      unrankedScoreboardEntryBuilder.silverMedals(10L);
      unrankedScoreboardEntryBuilder.bronzeMedals(1L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testTeam1.getMembers().get(0));
      unrankedScoreboardEntryBuilder.team(testTeam1);
      unrankedScoreboardEntryBuilder.displayName(testTeam1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(100L);
      unrankedScoreboardEntryBuilder.baseScore(100L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(50L);
      unrankedScoreboardEntryBuilder.silverMedals(5L);
      unrankedScoreboardEntryBuilder.bronzeMedals(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getSolverType)
        .containsExactly(SolverType.USER, SolverType.TEAM);
    }

    @Test
    @DisplayName("Can produce a scoreboard with two users with positive scores")
    void refreshScoreboard_TwoUsersWithPositiveScores_CreatesScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      unrankedScoreboardEntryBuilder.scoreAdjustment(0L);

      unrankedScoreboardEntryBuilder.user(testUser1);
      unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      unrankedScoreboardEntryBuilder.score(1000L);
      unrankedScoreboardEntryBuilder.baseScore(1000L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(100L);
      unrankedScoreboardEntryBuilder.silverMedals(10L);
      unrankedScoreboardEntryBuilder.bronzeMedals(1L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

      unrankedScoreboardEntryBuilder.user(testUser2);
      unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
      unrankedScoreboardEntryBuilder.score(100L);
      unrankedScoreboardEntryBuilder.baseScore(100L);
      unrankedScoreboardEntryBuilder.bonusScore(0L);
      unrankedScoreboardEntryBuilder.goldMedals(50L);
      unrankedScoreboardEntryBuilder.silverMedals(5L);
      unrankedScoreboardEntryBuilder.bronzeMedals(0L);
      final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

      when(rankedSubmissionRepository.getUnrankedScoreboard())
        .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }

    @Nested
    @DisplayName("Can produce a scoreboard with two users that differ only by")
    class tiebreakWithMedals {

      UnrankedScoreboardEntry unrankedScoreboardEntry2;

      UserEntity testUser1;
      UserEntity testUser2;

      @BeforeEach
      void setup() {
        testUser1 = createTestUser();
        testUser2 = createTestUser();

        when(userService.findAllSolvers())
          .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

        unrankedScoreboardEntryBuilder.user(testUser2);
        unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());

        unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

        unrankedScoreboardEntryBuilder.user(testUser1);
        unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
      }

      private void testScoreboard() {
        StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

        verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
        ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

        assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 2L);

        assertThat(computedScoreboard)
          .extracting(ScoreboardEntry::getPrincipalId)
          .containsExactly(testUser1.getId(), testUser2.getId());
      }

      @Test
      @DisplayName("gold medals")
      void refreshScoreboard_TwoUsersDifferingGoldMedals_CreatesScoreboard() {
        unrankedScoreboardEntryBuilder.goldMedals(1L);

        when(rankedSubmissionRepository.getUnrankedScoreboard())
          .thenReturn(Flux.just(unrankedScoreboardEntryBuilder.build(), unrankedScoreboardEntry2));

        testScoreboard();
      }

      @Test
      @DisplayName("silver medals")
      void refreshScoreboard_TwoUsersDifferingSilverMedals_CreatesScoreboard() {
        unrankedScoreboardEntryBuilder.silverMedals(1L);

        when(rankedSubmissionRepository.getUnrankedScoreboard())
          .thenReturn(Flux.just(unrankedScoreboardEntryBuilder.build(), unrankedScoreboardEntry2));

        testScoreboard();
      }

      @Test
      @DisplayName("bronze medals")
      void refreshScoreboard_TwoUsersDifferingBronzeMedals_CreatesScoreboard() {
        unrankedScoreboardEntryBuilder.bronzeMedals(1L);

        when(rankedSubmissionRepository.getUnrankedScoreboard())
          .thenReturn(Flux.just(unrankedScoreboardEntryBuilder.build(), unrankedScoreboardEntry2));

        testScoreboard();
      }
    }

    @Test
    @DisplayName("Can produce a scoreboard with zero-score users when no submissions are present")
    void refreshScoreboard_TwoUsersWithoutSubmissions_CreatesZeroedScoreboard() {
      final UserEntity testUser1 = createTestUser();
      final UserEntity testUser2 = createTestUser();

      when(userService.findAllSolvers())
        .thenReturn(Flux.just(testUser1, testUser2).map(user -> createSolverEntityFromUser(user)));

      StepVerifier.create(scoreboardService.refreshScoreboard()).verifyComplete();

      verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
      ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

      assertThat(computedScoreboard).extracting(ScoreboardEntry::getRank).containsExactly(1L, 1L);

      assertThat(computedScoreboard)
        .extracting(ScoreboardEntry::getPrincipalId)
        .containsExactly(testUser1.getId(), testUser2.getId());
    }
  }
}
