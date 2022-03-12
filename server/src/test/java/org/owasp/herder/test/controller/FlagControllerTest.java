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

import java.time.LocalDateTime;
import java.time.Month;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.flag.FlagController;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagController unit tests")
class FlagControllerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private FlagController flagController;

  @Mock private ControllerAuthentication controllerAuthentication;

  @Mock private ModuleService moduleService;

  @Mock private SubmissionService submissionService;

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    flagController = new FlagController(controllerAuthentication, moduleService, submissionService);
  }

  @Test
  void submitFlag_UserNotAuthenticated_ReturnsException() throws Exception {
    final String flag = "validflag";
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(controllerAuthentication.getUserId())
        .thenReturn(Mono.error(new NotAuthenticatedException()));

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    StepVerifier.create(flagController.submitFlag(TestConstants.TEST_MODULE_LOCATOR, flag))
        .expectError(NotAuthenticatedException.class)
        .verify();

    verify(controllerAuthentication, times(1)).getUserId();
  }

  @Test
  void submitFlag_UserAuthenticatedAndValidFlagSubmitted_ReturnsValidSubmission() throws Exception {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final String flag = "validflag";

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    final Submission submission =
        Submission.builder()
            .userId(TestConstants.TEST_USER_ID)
            .moduleId(TestConstants.TEST_MODULE_ID)
            .flag(flag)
            .isValid(true)
            .time(LocalDateTime.of(2000, Month.JULY, 1, 2, 3, 4))
            .build();

    when(submissionService.submit(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
        .thenReturn(Mono.just(submission));

    when(mockModule.getId()).thenReturn(TestConstants.TEST_MODULE_ID);

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    StepVerifier.create(flagController.submitFlag(TestConstants.TEST_MODULE_LOCATOR, flag))
        .expectNext(new ResponseEntity<Submission>(submission, HttpStatus.OK))
        .verifyComplete();

    verify(submissionService, times(1))
        .submit(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag);
  }
}
