/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.it.module.xss;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.xss.XssService;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.module.xss.XssTutorialResponse;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("XssTutorial integration test")
class XssTutorialIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  XssTutorial xssTutorial;

  @Autowired TestUtils testUtils;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired SubmissionService submissionService;

  @Autowired ScoreService scoreService;

  @Autowired XssService xssService;

  @Autowired FlagHandler flagHandler;

  @BeforeEach
  private void setUp() {
    testUtils.deleteAll().block();
    xssTutorial = new XssTutorial(xssService, moduleService, flagHandler);
    xssTutorial.getInit().block();
  }

  @AfterEach
  private void clear() {
    xssTutorial = null;
  }

  private String extractFlagFromResponse(final XssTutorialResponse response) {
    assertThat(response.getResult()).startsWith("Congratulations, flag is");
    return response.getResult().replaceAll("Congratulations, flag is ", "");
  }

  @Test
  void submitQuery_XssQuery_ShowsAlert() {
    final Long userId = userService.create("TestUser1").block();

    final Mono<String> flagMono =
        xssTutorial
            .submitQuery(userId, "<script>alert('xss')</script>")
            .map(this::extractFlagFromResponse);

    // Submit the flag we got from the sql injection and make sure it validates
    StepVerifier.create(
            flagMono
                .flatMap(
                    flag -> submissionService.submit(userId, xssTutorial.getModuleName(), flag))
                .map(Submission::isValid))
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_CorrectAttackQuery_ModifiedFlagIsWrong() {
    final Long userId = userService.create("TestUser1").block();

    final Mono<String> flagMono =
        xssTutorial
            .submitQuery(userId, "<script>alert('xss')</script>")
            .map(this::extractFlagFromResponse);

    // Take the flag we got from the tutorial, modify it, and expect validation to fail
    StepVerifier.create(
            flagMono
                .flatMap(flag -> submissionService.submit(userId, "xss-tutorial", flag + "wrong"))
                .map(Submission::isValid))
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_QueryWithoutXss_NoResults() {
    final Long userId = userService.create("TestUser1").block();

    StepVerifier.create(xssTutorial.submitQuery(userId, "test"))
        .assertNext(
            response -> {
              assertThat(response.getResult()).startsWith("Sorry");
            })
        .expectComplete()
        .verify();
  }
}
