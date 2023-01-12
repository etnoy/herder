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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.UnrankedScoreboardEntry;
import org.owasp.herder.scoring.UnrankedScoreboardEntry.UnrankedScoreboardEntryBuilder;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.PrincipalEntity;
import org.owasp.herder.user.PrincipalEntity.PrincipalEntityBuilder;
import org.owasp.herder.user.RefresherService;
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
class RefresherServiceTest extends BaseTest {

  private RefresherService refresherService;

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

  ArgumentCaptor<ArrayList<ScoreboardEntry>> scoreboardCaptor;

  int testUserIndex;

  @BeforeEach
  void setup() {
    // Set up the system under test
    refresherService =
      new RefresherService(
        submissionRepository,
        scoreboardRepository,
        rankedSubmissionRepository,
        userService,
        teamRepository,
        userRepository
      );

    when(scoreboardRepository.deleteAll()).thenReturn(Mono.empty());
    when(scoreboardRepository.saveAll(any(ArrayList.class))).thenAnswer(list -> Flux.empty());

    scoreboardCaptor = ArgumentCaptor.forClass(ArrayList.class);

    when(rankedSubmissionRepository.getUnrankedScoreboard()).thenReturn(Flux.empty());
    when(userService.findAllPrincipals()).thenReturn(Flux.empty());

    testUserIndex = 1;
  }

  @Test
  @DisplayName("Can produce an empty scoreboard when no users or submissions are present")
  void refreshScoreboard_NoUsersAndNoSubmissions_CreatesEmptyScoreboard() {
    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

    assertThat(newScoreboard).isEmpty();
  }

  private PrincipalEntity createPrincipalEntityFromUser(final UserEntity userEntity) {
    final PrincipalEntityBuilder principalEntityBuilder = PrincipalEntity.builder();
    principalEntityBuilder.creationTime(LocalDateTime.MIN);
    principalEntityBuilder.principalType(PrincipalType.USER);

    principalEntityBuilder.displayName(userEntity.getDisplayName());
    principalEntityBuilder.id(userEntity.getId());
    return principalEntityBuilder.build();
  }

  private UserEntity createTestUser() {
    final UserEntityBuilder userEntityBuilder = UserEntity.builder();
    userEntityBuilder.key(TestConstants.TEST_BYTE_ARRAY);
    userEntityBuilder.id("id" + testUserIndex);
    userEntityBuilder.displayName("Test User " + testUserIndex);

    this.testUserIndex++;
    return userEntityBuilder.build();
  }

  @Test
  @DisplayName("Can produce a scoreboard with users scoring positive, zero, zero-adjusted, and negative scores")
  void refreshScoreboard_PositiveZeroZeroAdjustedAndNegativeScores_CreatesScoreboard() {
    final UserEntity testUser1 = createTestUser();
    final UserEntity testUser2 = createTestUser();
    final UserEntity testUser3 = createTestUser();
    final UserEntity testUser4 = createTestUser();

    final UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();

    unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
    unrankedScoreboardEntryBuilder.user(testUser1);
    unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
    unrankedScoreboardEntryBuilder.score(1000L);
    unrankedScoreboardEntryBuilder.baseScore(1000L);
    unrankedScoreboardEntryBuilder.bonusScore(0L);
    unrankedScoreboardEntryBuilder.goldMedals(0L);
    unrankedScoreboardEntryBuilder.silverMedals(0L);
    unrankedScoreboardEntryBuilder.bronzeMedals(0L);
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
    final UnrankedScoreboardEntry unrankedScoreboardEntry3 = unrankedScoreboardEntryBuilder.build();

    when(rankedSubmissionRepository.getUnrankedScoreboard())
      .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2, unrankedScoreboardEntry3));

    when(userService.findAllPrincipals())
      .thenReturn(Flux.just(testUser1, testUser2, testUser3, testUser4).map(this::createPrincipalEntityFromUser));

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

    assertThat(computedScoreboard).hasSize(4);

    final ScoreboardEntry entry1 = computedScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(testUser1.getDisplayName());
    assertThat(entry1.getScore()).isEqualTo(1000L);
    assertThat(entry1.getGoldMedals()).isZero();
    assertThat(entry1.getSilverMedals()).isZero();
    assertThat(entry1.getBronzeMedals()).isZero();

    final ScoreboardEntry entry2 = computedScoreboard.get(1);
    assertThat(entry2.getRank()).isEqualTo(2L);
    assertThat(entry2.getDisplayName()).isEqualTo(testUser2.getDisplayName());
    assertThat(entry2.getScore()).isZero();
    assertThat(entry2.getGoldMedals()).isZero();
    assertThat(entry2.getSilverMedals()).isZero();
    assertThat(entry2.getBronzeMedals()).isZero();

    final ScoreboardEntry entry3 = computedScoreboard.get(2);
    assertThat(entry3.getRank()).isEqualTo(2L);
    assertThat(entry3.getDisplayName()).isEqualTo(testUser4.getDisplayName());
    assertThat(entry3.getScore()).isZero();
    assertThat(entry3.getGoldMedals()).isZero();
    assertThat(entry3.getSilverMedals()).isZero();
    assertThat(entry3.getBronzeMedals()).isZero();

    final ScoreboardEntry entry4 = computedScoreboard.get(3);
    assertThat(entry4.getRank()).isEqualTo(4L);
    assertThat(entry4.getDisplayName()).isEqualTo(testUser3.getDisplayName());
    assertThat(entry4.getScore()).isEqualTo(-100L);
    assertThat(entry4.getGoldMedals()).isZero();
    assertThat(entry4.getSilverMedals()).isZero();
    assertThat(entry4.getBronzeMedals()).isZero();
  }

  @Test
  @DisplayName("Can produce a scoreboard with users scoring positive, zero, and negative scores")
  void refreshScoreboard_PositiveZeroAndNegativeScoresd_CreatesScoreboard() {
    final UserEntity testUser1 = createTestUser();
    final UserEntity testUser2 = createTestUser();
    final UserEntity testUser3 = createTestUser();

    final UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();

    unrankedScoreboardEntryBuilder.scoreAdjustment(0L);
    unrankedScoreboardEntryBuilder.user(testUser1);
    unrankedScoreboardEntryBuilder.displayName(testUser1.getDisplayName());
    unrankedScoreboardEntryBuilder.score(1000L);
    unrankedScoreboardEntryBuilder.baseScore(1000L);
    unrankedScoreboardEntryBuilder.bonusScore(0L);
    unrankedScoreboardEntryBuilder.goldMedals(0L);
    unrankedScoreboardEntryBuilder.silverMedals(0L);
    unrankedScoreboardEntryBuilder.bronzeMedals(0L);
    final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

    unrankedScoreboardEntryBuilder.user(testUser2);
    unrankedScoreboardEntryBuilder.displayName(testUser2.getDisplayName());
    unrankedScoreboardEntryBuilder.score(-100L);
    unrankedScoreboardEntryBuilder.baseScore(0L);
    final UnrankedScoreboardEntry unrankedScoreboardEntry2 = unrankedScoreboardEntryBuilder.build();

    when(rankedSubmissionRepository.getUnrankedScoreboard())
      .thenReturn(Flux.just(unrankedScoreboardEntry1, unrankedScoreboardEntry2));

    when(userService.findAllPrincipals())
      .thenReturn(Flux.just(testUser1, testUser2, testUser3).map(this::createPrincipalEntityFromUser));

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

    assertThat(computedScoreboard).hasSize(3);

    final ScoreboardEntry entry1 = computedScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(testUser1.getDisplayName());
    assertThat(entry1.getScore()).isEqualTo(1000L);
    assertThat(entry1.getGoldMedals()).isZero();
    assertThat(entry1.getSilverMedals()).isZero();
    assertThat(entry1.getBronzeMedals()).isZero();

    final ScoreboardEntry entry2 = computedScoreboard.get(1);
    assertThat(entry2.getRank()).isEqualTo(2L);
    assertThat(entry2.getDisplayName()).isEqualTo(testUser3.getDisplayName());
    assertThat(entry2.getScore()).isZero();
    assertThat(entry2.getGoldMedals()).isZero();
    assertThat(entry2.getSilverMedals()).isZero();
    assertThat(entry2.getBronzeMedals()).isZero();

    final ScoreboardEntry entry3 = computedScoreboard.get(2);
    assertThat(entry3.getRank()).isEqualTo(3L);
    assertThat(entry3.getDisplayName()).isEqualTo(testUser2.getDisplayName());
    assertThat(entry3.getScore()).isEqualTo(-100L);
    assertThat(entry3.getGoldMedals()).isZero();
    assertThat(entry3.getSilverMedals()).isZero();
    assertThat(entry3.getBronzeMedals()).isZero();
  }

  @Test
  @DisplayName("Can produce a scoreboard with two users with positive scores")
  void refreshScoreboard_TwoUsersWithPositiveScores_CreatesScoreboard() {
    final UserEntity testUser1 = createTestUser();
    final UserEntity testUser2 = createTestUser();

    when(userService.findAllPrincipals())
      .thenReturn(Flux.just(testUser1, testUser2).map(this::createPrincipalEntityFromUser));

    UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();
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

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

    assertThat(computedScoreboard).hasSize(2);

    final ScoreboardEntry entry1 = computedScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(testUser1.getDisplayName());
    assertThat(entry1.getScore()).isEqualTo(1000L);
    assertThat(entry1.getGoldMedals()).isEqualTo(100L);
    assertThat(entry1.getSilverMedals()).isEqualTo(10L);
    assertThat(entry1.getBronzeMedals()).isEqualTo(1L);

    final ScoreboardEntry entry2 = computedScoreboard.get(1);
    assertThat(entry2.getRank()).isEqualTo(2L);
    assertThat(entry2.getDisplayName()).isEqualTo(testUser2.getDisplayName());
    assertThat(entry2.getScore()).isEqualTo(100L);
    assertThat(entry2.getGoldMedals()).isEqualTo(50L);
    assertThat(entry2.getSilverMedals()).isEqualTo(5L);
    assertThat(entry2.getBronzeMedals()).isZero();
  }

  @Test
  @DisplayName("Can produce a scoreboard with zero-score users when no submissions are present")
  void refreshScoreboard_TwoUsersWithoutSubmissions_CreatesZeroedScoreboard() {
    final UserEntity testUser1 = createTestUser();
    final UserEntity testUser2 = createTestUser();

    when(userService.findAllPrincipals())
      .thenReturn(Flux.just(testUser1, testUser2).map(this::createPrincipalEntityFromUser));

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> computedScoreboard = scoreboardCaptor.getValue();

    assertThat(computedScoreboard).hasSize(2);

    final ScoreboardEntry entry1 = computedScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(testUser1.getDisplayName());
    assertThat(entry1.getScore()).isZero();
    assertThat(entry1.getGoldMedals()).isZero();
    assertThat(entry1.getSilverMedals()).isZero();
    assertThat(entry1.getBronzeMedals()).isZero();

    final ScoreboardEntry entry2 = computedScoreboard.get(1);
    assertThat(entry2.getRank()).isOne();
    assertThat(entry2.getDisplayName()).isEqualTo(testUser2.getDisplayName());
    assertThat(entry2.getScore()).isZero();
    assertThat(entry2.getGoldMedals()).isZero();
    assertThat(entry2.getSilverMedals()).isZero();
    assertThat(entry2.getBronzeMedals()).isZero();
  }
}
