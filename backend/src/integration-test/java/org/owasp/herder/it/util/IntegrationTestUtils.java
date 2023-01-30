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
package org.owasp.herder.it.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.configuration.ConfigurationRepository;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfAttackRepository;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.ScoreAdjustmentRepository;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassRepository;
import org.owasp.herder.user.ModuleListRepository;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.TeamService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public final class IntegrationTestUtils {

  @Autowired
  UserService userService;

  @Autowired
  TeamService teamService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  UserRepository userRepository;

  @Autowired
  PasswordAuthRepository passwordAuthRepository;

  @Autowired
  ConfigurationRepository configurationRepository;

  @Autowired
  ClassRepository classRepository;

  @Autowired
  TeamRepository teamRepository;

  @Autowired
  ModuleRepository moduleRepository;

  @Autowired
  ModuleListRepository moduleListRepository;

  @Autowired
  CsrfAttackRepository csrfAttackRepository;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  ScoreAdjustmentRepository scoreAdjustmentRepository;

  @Autowired
  ScoreboardRepository scoreboardRepository;

  @Autowired
  RankedSubmissionRepository rankedSubmissionRepository;

  @Autowired
  WebTokenService webTokenService;

  String moduleId;

  public void checkConstraintViolation(final ThrowingCallable shouldRaiseThrowable, final String containedMessage) {
    assertThatThrownBy(shouldRaiseThrowable)
      .isInstanceOf(ConstraintViolationException.class)
      .hasMessageContaining(containedMessage);
  }

  public String createDynamicTestModule() {
    final String moduleId = moduleService
      .create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR)
      .block();

    moduleService.setDynamicFlag(moduleId).block();
    return moduleId;
  }

  public String createStaticTestModule() {
    final String moduleId = moduleService
      .create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR)
      .block();

    moduleService.setStaticFlag(moduleId, TestConstants.TEST_STATIC_FLAG).block();
    return moduleId;
  }

  public String createTestTeam() {
    return teamService.create(TestConstants.TEST_TEAM_DISPLAY_NAME).block();
  }

  public String createTestAdmin() {
    final String userId = userService
      .createPasswordUser(
        TestConstants.TEST_ADMIN_DISPLAY_NAME,
        TestConstants.TEST_ADMIN_LOGIN_NAME,
        TestConstants.HASHED_TEST_PASSWORD
      )
      .block();

    userService.promote(userId).block();
    return userId;
  }

  public String createTestUser() {
    return userService
      .createPasswordUser(
        TestConstants.TEST_USER_DISPLAY_NAME,
        TestConstants.TEST_USER_LOGIN_NAME,
        TestConstants.HASHED_TEST_PASSWORD
      )
      .block();
  }

  public ResponseSpec performAPILogin(String username, String password) {
    return webTestClient
      .post()
      .uri("/api/v1/login")
      .contentType(MediaType.APPLICATION_JSON)
      .body(
        BodyInserters.fromPublisher(
          Mono.just("{\"userName\": \"" + username + "\", \"password\": \"" + password + "\"}"),
          String.class
        )
      )
      .exchange();
  }

  public String performAPILoginWithToken(final String username, final String password) {
    return JsonPath
      .parse(
        new String(
          performAPILogin(username, password)
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody()
        )
      )
      .read("$.accessToken");
  }

  public void resetState() {
    scoreAdjustmentRepository
      .deleteAll()
      // Delete all csrf attacks
      .then(csrfAttackRepository.deleteAll())
      // Delete all submissions
      .then(submissionRepository.deleteAll())
      // Delete all ranked scoreboard entries
      .then(rankedSubmissionRepository.deleteAll())
      // Delete all scoreboard entries
      .then(scoreboardRepository.deleteAll())
      // Delete all classes
      .then(classRepository.deleteAll())
      // Delete all modules
      .then(moduleRepository.deleteAll())
      // Delete all module lists
      .then(moduleListRepository.deleteAll())
      // Delete all configurations
      .then(configurationRepository.deleteAll())
      // Delete all teams
      .then(teamRepository.deleteAll())
      // Delete all password auth data
      .then(passwordAuthRepository.deleteAll())
      // Delete all users
      .then(userRepository.deleteAll())
      .block();
  }

  public ResponseSpec submitFlagApi(final String moduleLocator, final String token, final String flag) {
    if ((moduleLocator == null) || (flag == null)) {
      throw new NullPointerException();
    }

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleLocator);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody = BodyInserters.fromValue(flag);
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

  public Flux<Submission> submitFlagApiAndReturnSubmission(
    final String moduleLocator,
    final String token,
    final String flag
  ) {
    if ((moduleLocator == null) || (token == null) || (flag == null)) {
      throw new NullPointerException();
    }

    return submitFlagApi(moduleLocator, token, flag)
      .expectStatus()
      .isOk()
      .returnResult(Submission.class)
      .getResponseBody();
  }

  public Submission submitInvalidFlag(final String userId, final String moduleId) {
    return submissionService.submitFlag(userId, moduleId, "an-invalid-flag-cant-be-right").block();
  }

  public Submission submitValidFlag(final String userId, final String moduleId) {
    final ModuleEntity moduleEntity = moduleService.findById(moduleId).block();

    String validFlag;

    if (moduleEntity.isFlagStatic()) {
      validFlag = moduleEntity.getStaticFlag();
    } else {
      validFlag = flagHandler.getDynamicFlag(userId, moduleEntity.getLocator()).block();
    }

    return submissionService.submitFlag(userId, moduleId, validFlag).block();
  }
}
