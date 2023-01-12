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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.UnrankedScoreboardEntry;
import org.owasp.herder.scoring.UnrankedScoreboardEntry.UnrankedScoreboardEntryBuilder;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
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
  }

  @Test
  void refreshScoreboard_EmptyScoreboardAndNoUsers_CreatesEmptyScoreboard() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<ArrayList<ScoreboardEntry>> scoreboardCaptor = ArgumentCaptor.forClass(ArrayList.class);

    when(rankedSubmissionRepository.getUnrankedScoreboard()).thenReturn(Flux.empty());
    when(userService.findAllPrincipals()).thenReturn(Flux.empty());
    when(scoreboardRepository.deleteAll()).thenReturn(Mono.empty());
    when(scoreboardRepository.saveAll(new ArrayList<>())).thenReturn(Flux.empty());

    StepVerifier.create(refresherService.refreshScoreboard()).verifyComplete();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());

    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();

    assertThat(newScoreboard).isEmpty();
  }

  @Test
  void refreshScoreboard_NonEmptyScoreboard_CreatesEmptyScoreboard() {
    UnrankedScoreboardEntryBuilder unrankedScoreboardEntryBuilder = UnrankedScoreboardEntry.builder();
    unrankedScoreboardEntryBuilder.user(UserEntity.builder().id("1").build());
    unrankedScoreboardEntryBuilder.score(1000L);
    unrankedScoreboardEntryBuilder.goldMedals(100L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<ArrayList<ScoreboardEntry>> scoreboardCaptor = ArgumentCaptor.forClass(ArrayList.class);

    refresherService.refreshScoreboard();

    verify(scoreboardRepository).saveAll(scoreboardCaptor.capture());

    ArrayList<ScoreboardEntry> newScoreboard = scoreboardCaptor.getValue();
  }
}
