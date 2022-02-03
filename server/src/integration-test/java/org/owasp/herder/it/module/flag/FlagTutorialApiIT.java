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
package org.owasp.herder.it.module.flag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Flag Tutorial Module API integration tests")
class FlagTutorialApiIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  FlagTutorial flagTutorial;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired SubmissionService submissionService;

  @Autowired ScoreService scoreService;

  @Autowired WebTestClient webTestClient;

  @Autowired FlagHandler flagHandler;

  @Autowired IntegrationTestUtils integrationTestUtils;

  private final String moduleName = "flag-tutorial";

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
    flagTutorial = new FlagTutorial(moduleService, flagHandler);
    flagTutorial.getInit().block();
  }

  @Test
  @DisplayName("The flag returned by the tutorial should be valid")
  void canAcceptTheCorrectFlag() throws Exception {
    integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD);

    final String endpoint = "/api/v1/module/flag-tutorial/";

    final String flag =
        JsonPath.parse(
                new String(
                    webTestClient
                        .get()
                        .uri(endpoint)
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .returnResult(String.class)
                        .getResponseBody()
                        .blockFirst()))
            .read("$.flag");

    StepVerifier.create(
            integrationTestUtils
                .submitFlagAndReturnSubmission(moduleName, accessToken, flag)
                .map(Submission::isValid))
        .expectNext(true)
        .expectComplete()
        .verify();
  }
}
