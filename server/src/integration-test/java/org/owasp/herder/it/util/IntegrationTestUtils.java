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
package org.owasp.herder.it.util;

import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.authentication.UserAuthRepository;
import org.owasp.herder.configuration.ConfigurationRepository;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.module.ModulePointRepository;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfAttackRepository;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassRepository;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.stereotype.Service;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient.RequestBodySpec;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

import com.jayway.jsonpath.JsonPath;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public final class IntegrationTestUtils extends BaseIT {
  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired WebTestClient webTestClient;

  @Autowired UserRepository userRepository;

  @Autowired PasswordAuthRepository passwordAuthRepository;

  @Autowired ConfigurationRepository configurationRepository;

  @Autowired ClassRepository classRepository;

  @Autowired ModuleRepository moduleRepository;

  @Autowired CsrfAttackRepository csrfAttackRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired CorrectionRepository correctionRepository;

  @Autowired ModulePointRepository modulePointRepository;

  @Autowired UserAuthRepository userAuthRepository;

  @Autowired WebTokenService webTokenService;

  public void createDynamicTestModule() {
    moduleService
        .create(TestConstants.TEST_MODULE_NAME)
        .then(moduleService.setDynamicFlag(TestConstants.TEST_MODULE_NAME))
        .block();
  }

  public void createStaticTestModule() {
    moduleService
        .create(TestConstants.TEST_MODULE_NAME)
        .then(
            moduleService.setStaticFlag(
                TestConstants.TEST_MODULE_NAME, TestConstants.TEST_STATIC_FLAG))
        .block();
  }

  public Long createTestAdmin() {
    final long userId =
        userService
            .createPasswordUser(
                TestConstants.TEST_DISPLAY_NAME,
                TestConstants.TEST_LOGIN_NAME,
                TestConstants.HASHED_TEST_PASSWORD)
            .block();

    userService.promote(userId).block();
    return userId;
  }

  public Long createTestUser() {
    return userService
        .createPasswordUser(
            TestConstants.TEST_DISPLAY_NAME,
            TestConstants.TEST_LOGIN_NAME,
            TestConstants.HASHED_TEST_PASSWORD)
        .block();
  }

  public ResponseSpec performAPILogin(String username, String password) {
    return webTestClient
        .post()
        .uri("/api/v1/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromPublisher(
                Mono.just(
                    "{\"userName\": \"" + username + "\", \"password\": \"" + password + "\"}"),
                String.class))
        .exchange();
  }

  public String performAPILoginWithToken(String username, String password) {
    return JsonPath.parse(
            new String(
                performAPILogin(TestConstants.TEST_LOGIN_NAME, TestConstants.TEST_PASSWORD)
                    .expectStatus()
                    .isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody()))
        .read("$.accessToken");
  }

  public void resetState() {
    // The special nature of the web token clock means it must be reset between tests
    webTokenService.resetClock();

    // Deleting data must be done in the right order due to db constraints
    // Delete all score corrections
    correctionRepository
        .deleteAll()
        // Delete all module scoring rules
        .then(csrfAttackRepository.deleteAll())
        // Delete all module scoring rules
        .then(modulePointRepository.deleteAll())
        // Delete all submissions
        .then(submissionRepository.deleteAll())
        // Delete all classes
        .then(classRepository.deleteAll())
        // Delete all modules
        .then(moduleRepository.deleteAll())
        // Delete all configuration
        .then(configurationRepository.deleteAll())
        // Delete all password auth data
        .then(passwordAuthRepository.deleteAll())
        // Delete all user auth data
        .then(userAuthRepository.deleteAll())
        // Delete all users
        .then(userRepository.deleteAll())
        .block();
  }

  public ResponseSpec submitFlag(final String moduleName, final String token, final String flag) {
    if ((moduleName == null) || (flag == null)) {
      throw new NullPointerException();
    }

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag);
    RequestBodySpec requestBodySpec = webTestClient.post().uri(endpoint);

    if (token != null) {
      requestBodySpec = requestBodySpec.header("Authorization", "Bearer " + token);
    }

    return requestBodySpec
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(submissionBody)
        .exchange();
  }

  public Flux<Submission> submitFlagAndReturnSubmission(
      final String moduleName, final String token, final String flag) {
    if ((moduleName == null) || (token == null) || (flag == null)) {
      throw new NullPointerException();
    }

    return submitFlag(moduleName, token, flag)
        .expectStatus()
        .isOk()
        .returnResult(Submission.class)
        .getResponseBody();
  }
}
