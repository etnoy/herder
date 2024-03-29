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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.FlagSubmissionRateLimitException;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.flag.FlagController;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagController unit tests")
class FlagControllerTest extends BaseTest {

  private FlagController flagController;

  @Mock
  private ControllerAuthentication controllerAuthentication;

  @Mock
  private ModuleService moduleService;

  @Mock
  private SubmissionService submissionService;

  @Mock
  private ScoreboardService scoreboardService;

  @BeforeEach
  void setup() {
    flagController = new FlagController(controllerAuthentication, moduleService, submissionService, scoreboardService);
  }

  @Test
  @DisplayName("Can error when submitting flag without being authenticated")
  void submitFlag_UserNotAuthenticated_Errors() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.error(new NotAuthenticatedException()));

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    StepVerifier
      .create(flagController.submitFlag(TestConstants.TEST_MODULE_LOCATOR, TestConstants.TEST_STATIC_FLAG))
      .expectError(NotAuthenticatedException.class)
      .verify();

    verify(controllerAuthentication, times(1)).getUserId();
  }

  @Test
  @DisplayName("Can submit flag")
  void submitFlag_UserAuthenticatedAndValidFlagSubmitted_ReturnsValidSubmission() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    final Submission submission = Submission
      .builder()
      .userId(TestConstants.TEST_USER_ID)
      .moduleId(TestConstants.TEST_MODULE_ID)
      .flag(TestConstants.TEST_STATIC_FLAG)
      .isValid(true)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();

    when(
      submissionService.submitFlag(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_ID,
        TestConstants.TEST_STATIC_FLAG
      )
    )
      .thenReturn(Mono.just(submission));

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withId(TestConstants.TEST_MODULE_ID)));

    when(moduleService.refreshModuleLists()).thenReturn(Mono.empty());
    when(submissionService.refreshSubmissionRanks()).thenReturn(Mono.empty());
    when(scoreboardService.refreshScoreboard()).thenReturn(Mono.empty());

    StepVerifier
      .create(
        flagController
          .submitFlag(TestConstants.TEST_MODULE_LOCATOR, TestConstants.TEST_STATIC_FLAG)
          .map(ResponseEntity::getBody)
      )
      .expectNext(submission)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return too many requests status if rate limit is exceeded")
  void submitFlag_RateLimitExceeded_Errors() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    when(
      submissionService.submitFlag(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_ID,
        TestConstants.TEST_STATIC_FLAG
      )
    )
      .thenReturn(Mono.error(new FlagSubmissionRateLimitException()));

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withId(TestConstants.TEST_MODULE_ID)));

    StepVerifier
      .create(flagController.submitFlag(TestConstants.TEST_MODULE_LOCATOR, TestConstants.TEST_STATIC_FLAG))
      .assertNext(response -> {
        assertThat(response.getHeaders()).isEmpty();
        assertThat(response.getBody()).isNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
      })
      .verifyComplete();
  }
}
