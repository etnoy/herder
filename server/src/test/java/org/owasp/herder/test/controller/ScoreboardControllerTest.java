/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.test.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.scoring.RankedSubmission.RankedSubmissionBuilder;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.ScoreboardController;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardEntry.ScoreboardEntryBuilder;
import org.owasp.herder.scoring.SubmissionService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreboardController unit test")
class ScoreboardControllerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ScoreboardController scoreboardController;

  @Mock private ScoreService scoreService;

  @Mock private SubmissionService submissionService;

  @Test
  void getScoreboard_ValidData_ReturnsScoreboard() {
    final ScoreboardEntryBuilder scoreboardEntryBuilder = ScoreboardEntry.builder();
    final ScoreboardEntry scoreboardEntry1 =
        scoreboardEntryBuilder
            .rank(1L)
            .userId(1L)
            .score(1337L)
            .goldMedals(420L)
            .silverMedals(17L)
            .bronzeMedals(2L)
            .build();
    final ScoreboardEntry scoreboardEntry2 =
        scoreboardEntryBuilder
            .rank(1L)
            .userId(3L)
            .score(13399L)
            .goldMedals(69L)
            .silverMedals(19L)
            .bronzeMedals(2L)
            .build();

    final Flux<ScoreboardEntry> scoreboard = Flux.just(scoreboardEntry1, scoreboardEntry2);
    when(scoreService.getScoreboard()).thenReturn(scoreboard);

    StepVerifier.create(scoreboardController.getScoreboard())
        .expectNext(scoreboardEntry1)
        .expectNext(scoreboardEntry2)
        .expectComplete()
        .verify();

    verify(scoreService, times(1)).getScoreboard();
  }

  @Test
  void getScoreboardByUserId_ValidUserId_ReturnsScoreboardForUser() {
    final long mockUserId = 6331L;
    final RankedSubmissionBuilder rankedSubmissionBuilder = RankedSubmission.builder();
    final RankedSubmission rankedSubmission1 =
        rankedSubmissionBuilder.userId(2L).moduleName(9L).time(LocalDateTime.MIN).build();
    final RankedSubmission rankedSubmission2 =
        rankedSubmissionBuilder.userId(2342L).moduleName(9456L).time(LocalDateTime.MAX).build();

    final Flux<RankedSubmission> rankedSubmissions =
        Flux.just(rankedSubmission1, rankedSubmission2);
    when(submissionService.findAllRankedByUserId(mockUserId)).thenReturn(rankedSubmissions);

    StepVerifier.create(scoreboardController.getScoreboardByUserId(mockUserId))
        .expectNext(rankedSubmission1)
        .expectNext(rankedSubmission2)
        .expectComplete()
        .verify();
    verify(submissionService, times(1)).findAllRankedByUserId(mockUserId);
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    scoreboardController = new ScoreboardController(scoreService, submissionService);
  }
}
