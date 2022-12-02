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

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@DisplayName("Flag Tutorial API integration tests")
class FlagTutorialApiIT extends BaseIT {
  @Autowired ApplicationContext applicationContext;

  FlagTutorial flagTutorial;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired SubmissionService submissionService;

  @Autowired WebTestClient webTestClient;

  @Autowired FlagHandler flagHandler;

  @Autowired IntegrationTestUtils integrationTestUtils;

  ModuleInitializer moduleInitializer;

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    moduleInitializer = new ModuleInitializer(applicationContext, moduleService);

    flagTutorial = new FlagTutorial(flagHandler);

    moduleInitializer.initializeModule(flagTutorial).block();
  }

  @Test
  @DisplayName("The flag returned by the tutorial should be valid")
  void canAcceptTheCorrectFlag() {
    integrationTestUtils.createTestUser();

    final String accessToken =
        integrationTestUtils.performAPILoginWithToken(
            TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD);

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
                .submitFlagApiAndReturnSubmission(flagTutorial.getLocator(), accessToken, flag)
                .map(Submission::isValid))
        .expectNext(true)
        .verifyComplete();
  }
}
