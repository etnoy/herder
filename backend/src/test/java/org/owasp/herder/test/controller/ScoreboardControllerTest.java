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
package org.owasp.herder.test.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.SanitizedRankedSubmission;
import org.owasp.herder.scoring.ScoreboardController;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreboardController unit tests")
class ScoreboardControllerTest extends BaseTest {

  private ScoreboardController scoreboardController;

  @Mock
  private ScoreboardService scoreboardService;

  @Mock
  private UserService userService;

  @Mock
  private ModuleService moduleService;

  @Mock
  private SubmissionService submissionService;

  //  @Test
  //  void getScoreboard_ValidData_ReturnsScoreboard() {
  //    final ScoreboardEntryBuilder scoreboardEntryBuilder = ScoreboardEntry.builder();
  //    final ScoreboardEntry scoreboardEntry1 =
  //        scoreboardEntryBuilder
  //            .rank(1L)
  //            .userId("asdf")
  //            .score(1337L)
  //            .displayName("User1")
  //            .goldMedals(420L)
  //            .silverMedals(17L)
  //            .bronzeMedals(2L)
  //            .build();
  //    final ScoreboardEntry scoreboardEntry2 =
  //        scoreboardEntryBuilder
  //            .rank(1L)
  //            .userId("qwert")
  //            .score(13399L)
  //            .displayName("User2")
  //            .goldMedals(69L)
  //            .silverMedals(19L)
  //            .bronzeMedals(2L)
  //            .build();
  //
  //    final Flux<ScoreboardEntry> scoreboard = Flux.just(scoreboardEntry1, scoreboardEntry2);
  //    when(scoreboardService.getScoreboard()).thenReturn(scoreboard);
  //
  //    StepVerifier.create(scoreboardController.getScoreboard())
  //        .expectNext(scoreboardEntry1)
  //        .expectNext(scoreboardEntry2)
  //        .verifyComplete();
  //
  //    verify(scoreboardService, times(1)).getScoreboard();
  //  }

  @Test
  void getScoreboardByUserId_ValidUserId_ReturnsScoreboardForUser() {
    final String mockUserId = "id";

    final SanitizedRankedSubmission rankedSubmission1 = mock(
      SanitizedRankedSubmission.class
    );
    final SanitizedRankedSubmission rankedSubmission2 = mock(
      SanitizedRankedSubmission.class
    );

    final Flux<SanitizedRankedSubmission> rankedSubmissions = Flux.just(
      rankedSubmission1,
      rankedSubmission2
    );
    when(submissionService.findAllRankedByUserId(mockUserId))
      .thenReturn(rankedSubmissions);
    when(userService.existsById(mockUserId)).thenReturn(Mono.just(true));

    StepVerifier
      .create(scoreboardController.getSubmissionsByUserId(mockUserId))
      .expectNext(rankedSubmission1)
      .expectNext(rankedSubmission2)
      .verifyComplete();

    verify(submissionService, times(1)).findAllRankedByUserId(mockUserId);
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    scoreboardController =
      new ScoreboardController(
        scoreboardService,
        userService,
        submissionService,
        moduleService
      );
  }
}
