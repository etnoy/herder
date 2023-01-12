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
  }

  @Test
  @DisplayName("Can produce an empty scoreboard when no users or submissions are present")
  void refreshScoreboard_NoUsersAndNoSubmissions_CreatesEmptyScoreboard() {
    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

    assertThat(newScoreboard).isEmpty();
  }

  @Test
  void refreshScoreboard_ScoreboardWithOnlyUsers_CreatesEmptyScoreboard() {
    final UserEntityBuilder userEntityBuilder = UserEntity.builder();
    userEntityBuilder.key(TestConstants.TEST_BYTE_ARRAY);

    String displayName1 = "User 1";
    userEntityBuilder.id("id1");
    userEntityBuilder.displayName(displayName1);
    final UserEntity userEntity1 = userEntityBuilder.build();

    String displayName2 = "User 2";
    userEntityBuilder.id("id2");
    userEntityBuilder.displayName(displayName2);
    final UserEntity userEntity2 = userEntityBuilder.build();

    UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();
    unrankedScoreboardEntryBuilder.scoreAdjustment(0L);

    unrankedScoreboardEntryBuilder.user(userEntity1);
    unrankedScoreboardEntryBuilder.displayName(userEntity1.getDisplayName());
    unrankedScoreboardEntryBuilder.score(1000L);
    unrankedScoreboardEntryBuilder.baseScore(1000L);
    unrankedScoreboardEntryBuilder.bonusScore(0L);
    unrankedScoreboardEntryBuilder.goldMedals(100L);
    unrankedScoreboardEntryBuilder.silverMedals(10L);
    unrankedScoreboardEntryBuilder.bronzeMedals(1L);
    final UnrankedScoreboardEntry unrankedScoreboardEntry1 = unrankedScoreboardEntryBuilder.build();

    unrankedScoreboardEntryBuilder.user(userEntity2);
    unrankedScoreboardEntryBuilder.displayName(userEntity2.getDisplayName());
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

    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

    final ScoreboardEntry entry1 = newScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(displayName1);
    assertThat(entry1.getScore()).isEqualTo(1000L);
    assertThat(entry1.getGoldMedals()).isEqualTo(100L);
    assertThat(entry1.getSilverMedals()).isEqualTo(10L);
    assertThat(entry1.getBronzeMedals()).isEqualTo(1L);

    final ScoreboardEntry entry2 = newScoreboard.get(1);
    assertThat(entry2.getRank()).isEqualTo(2L);
    assertThat(entry2.getDisplayName()).isEqualTo(displayName2);
    assertThat(entry2.getScore()).isEqualTo(100L);
    assertThat(entry2.getGoldMedals()).isEqualTo(50L);
    assertThat(entry2.getSilverMedals()).isEqualTo(5L);
    assertThat(entry2.getBronzeMedals()).isZero();
  }

  @Test
  @DisplayName("Can produce a scoreboard with zero-score users when no submissions are present")
  void refreshScoreboard_UsersButNoSubmissions_CreatesZeroedScoreboard() {
    final PrincipalEntityBuilder principalEntityBuilder = PrincipalEntity.builder();
    principalEntityBuilder.creationTime(LocalDateTime.MIN);
    principalEntityBuilder.principalType(PrincipalType.USER);

    String displayName1 = "User 1";
    principalEntityBuilder.displayName(displayName1);
    principalEntityBuilder.id("id1");
    final PrincipalEntity principalEntity1 = principalEntityBuilder.build();

    String displayName2 = "User 2";
    principalEntityBuilder.displayName(displayName2);
    principalEntityBuilder.id("id2");
    final PrincipalEntity principalEntity2 = principalEntityBuilder.build();

    when(userService.findAllPrincipals()).thenReturn(Flux.just(principalEntity1, principalEntity2));

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());
    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

    final ScoreboardEntry entry1 = newScoreboard.get(0);
    assertThat(entry1.getRank()).isOne();
    assertThat(entry1.getDisplayName()).isEqualTo(displayName1);
    assertThat(entry1.getScore()).isZero();
    assertThat(entry1.getGoldMedals()).isZero();
    assertThat(entry1.getSilverMedals()).isZero();
    assertThat(entry1.getBronzeMedals()).isZero();

    final ScoreboardEntry entry2 = newScoreboard.get(1);
    assertThat(entry2.getRank()).isOne();
    assertThat(entry2.getDisplayName()).isEqualTo(displayName2);
    assertThat(entry2.getScore()).isZero();
    assertThat(entry2.getGoldMedals()).isZero();
    assertThat(entry2.getSilverMedals()).isZero();
    assertThat(entry2.getBronzeMedals()).isZero();
  }
}
