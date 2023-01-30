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
package org.owasp.herder.test.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolationException;
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
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserService;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreboardController unit tests")
class ScoreboardControllerTest extends BaseTest {

  ScoreboardController scoreboardController;

  @Mock
  ScoreboardService scoreboardService;

  @Mock
  UserService userService;

  @Mock
  TeamService teamService;

  @Mock
  ModuleService moduleService;

  @Mock
  SubmissionService submissionService;

  @Test
  void getScoreboardByUserId_ValidUserId_ReturnsScoreboard() {
    final SanitizedRankedSubmission rankedSubmission1 = mock(SanitizedRankedSubmission.class);
    final SanitizedRankedSubmission rankedSubmission2 = mock(SanitizedRankedSubmission.class);

    final Flux<SanitizedRankedSubmission> rankedSubmissions = Flux.just(rankedSubmission1, rankedSubmission2);
    when(submissionService.findAllRankedByUserId(TestConstants.TEST_USER_ID)).thenReturn(rankedSubmissions);
    when(userService.existsById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(true));

    StepVerifier
      .create(scoreboardController.getScoreboardByUserId(TestConstants.TEST_USER_ID))
      .expectNext(rankedSubmission1)
      .expectNext(rankedSubmission2)
      .verifyComplete();
  }

  @Test
  void getScoreboardByUserId_InvalidUserId_Errors() {
    when(userService.existsById(TestConstants.TEST_USER_ID))
      .thenThrow(new ConstraintViolationException("bad data", null));

    StepVerifier
      .create(scoreboardController.getScoreboardByUserId(TestConstants.TEST_USER_ID))
      .expectError(ResponseStatusException.class)
      .verify();
  }

  @Test
  void getScoreboardByModuleLocator_ValidLocator_ReturnsScoreboard() {
    final SanitizedRankedSubmission rankedSubmission1 = mock(SanitizedRankedSubmission.class);
    final SanitizedRankedSubmission rankedSubmission2 = mock(SanitizedRankedSubmission.class);

    final Flux<SanitizedRankedSubmission> rankedSubmissions = Flux.just(rankedSubmission1, rankedSubmission2);
    when(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(rankedSubmissions);
    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(true));

    StepVerifier
      .create(scoreboardController.getScoreboardByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(rankedSubmission1)
      .expectNext(rankedSubmission2)
      .verifyComplete();
  }

  @Test
  void getScoreboardByModuleLocator_InvalidLocator_ReturnsScoreboard() {
    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenThrow(new ConstraintViolationException("bad data", null));

    StepVerifier
      .create(scoreboardController.getScoreboardByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectError(ResponseStatusException.class)
      .verify();
  }

  @Test
  void getScoreboardByTeamId_ValidTeamId_ReturnsScoreboard() {
    final SanitizedRankedSubmission rankedSubmission1 = mock(SanitizedRankedSubmission.class);
    final SanitizedRankedSubmission rankedSubmission2 = mock(SanitizedRankedSubmission.class);

    final Flux<SanitizedRankedSubmission> rankedSubmissions = Flux.just(rankedSubmission1, rankedSubmission2);
    when(submissionService.findAllRankedByTeamId(TestConstants.TEST_TEAM_ID)).thenReturn(rankedSubmissions);
    when(teamService.existsById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(true));

    StepVerifier
      .create(scoreboardController.getScoreboardByTeamId(TestConstants.TEST_TEAM_ID))
      .expectNext(rankedSubmission1)
      .expectNext(rankedSubmission2)
      .verifyComplete();
  }

  @Test
  void getScoreboardByTeamId_InvalidTeamId_ReturnsScoreboard() {
    when(teamService.existsById(TestConstants.TEST_TEAM_ID))
      .thenThrow(new ConstraintViolationException("bad data", null));

    StepVerifier
      .create(scoreboardController.getScoreboardByTeamId(TestConstants.TEST_TEAM_ID))
      .expectError(ResponseStatusException.class)
      .verify();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    scoreboardController =
      new ScoreboardController(scoreboardService, userService, teamService, submissionService, moduleService);
  }
}
