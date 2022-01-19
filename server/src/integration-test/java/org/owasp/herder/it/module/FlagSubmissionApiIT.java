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
package org.owasp.herder.it.module;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleController;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("FlagController API integration tests")
class FlagSubmissionApiIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired TestUtils testService;

  @Autowired WebTestClient webTestClient;

  @Autowired ObjectMapper objectMapper;

  @Autowired FlagHandler flagHandler;

  @Autowired ModuleController moduleController;

  @Autowired PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("Submitting a valid dynamic flag should return true")
  void canAcceptValidDynamicFlag() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block();

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid dynamic flag surrounded by whitespace should return true")
  void canAcceptValidDynamicFlagIfSurroundedBySpaces() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block();

    final String flagWithSpaces = "    " + flag + "         ";

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flagWithSpaces);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid dynamic flag in lowercase should return true")
  void canAcceptValidDynamicFlagInLowercase() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block();

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag.toLowerCase());

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid dynamic flag in uppercase should return true")
  void canAcceptValidDynamicFlagInUppercase() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block();

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag.toUpperCase());

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid static flag should return true")
  void canAcceptValidStaticFlag() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String flag = "thisisaflag";

    moduleService.create(moduleName).block().getId();
    moduleService.setStaticFlag(moduleName, flag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        // We're done
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid static flag surrounded by spaces should return true")
  void canAcceptValidStaticFlagIfSurroundedBySpaces() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String flag = "thisisaflag";

    final String flagWithSpaces = "     " + flag + "         ";

    moduleService.create(moduleName).block().getId();
    moduleService.setStaticFlag(moduleName, flag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flagWithSpaces);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        // We're done
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid static flag in lowercase should return true")
  void canAcceptValidStaticFlagInLowercase() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String flag = "thisisaflagWITHSOMEUPPERCASEandlowercase";

    moduleService.create(moduleName).block().getId();
    moduleService.setStaticFlag(moduleName, flag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag.toLowerCase());

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        // We're done
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a valid static flag in uppercase should return true")
  void canAcceptValidStaticFlagInUppercase() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String flag = "thisisaflagWITHSOMEUPPERCASE";

    moduleService.create(moduleName).block().getId();
    moduleService.setStaticFlag(moduleName, flag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag.toUpperCase());

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be valid
        .expectNext(true)
        // We're done
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting a flag should return HTTP Unauthorized if not logged in")
  void canRejectFlagWhenNotLoggedIn() throws Exception {
    final String moduleName = "test-module";

    final String flag = "thisisaflag";

    moduleService.create(moduleName).block();

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag);

    webTestClient
        .post()
        .uri(endpoint)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(submissionBody)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("Submitting an invalid dynamic flag should return false")
  void canRejectInvalidDynamicFlag() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block() + "invalid";

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flag);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be invalid
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("Submitting an invalid static flag should return false")
  void canRejectInvalidStaticFlag() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String staticFlag = "thisisaflag";
    final byte[] byteFlag = {1, 2, 3, 4, 5};
    final String[] invalidStaticFlags = {"", "wrongflag", byteFlag.toString()};

    moduleService.create(moduleName).block();
    moduleService.setStaticFlag(moduleName, staticFlag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    for (String invalidFlag : invalidStaticFlags) {
      StepVerifier.create(
              webTestClient
                  .post()
                  .uri(String.format("/api/v1/flag/submit/%s", moduleName))
                  .header("Authorization", "Bearer " + token)
                  .accept(MediaType.APPLICATION_JSON)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(BodyInserters.fromValue(invalidFlag))
                  .exchange()
                  .expectStatus()
                  .isOk()
                  .returnResult(Submission.class)
                  .getResponseBody()
                  .map(Submission::isValid))
          .expectNext(false)
          .expectComplete()
          .verify();
    }
  }

  @Test
  @DisplayName(
      "Submitting a valid dynamic flag surrounded by whitespace other than spaces should return false")
  void canRejectValidDynamicFlagIfSurroundedByInvalidWhitespace() throws Exception {
    final String loginName = "testUser";
    final String password = "password";
    final String moduleName = "test-module";

    final String hashedPassword = passwordEncoder.encode(password);
    moduleService.create(moduleName).block();
    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String flag = flagHandler.getDynamicFlag(userId, moduleName).block();

    final String flagWithInvalidWhitespace = "\n" + flag + "\t";

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flagWithInvalidWhitespace);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be invalid
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName(
      "Submitting a valid static flag surrounded by whitespace other than spaces should return false")
  void canRejectValidStaticFlagIfSurroundedByInvalidWhitespace() throws Exception {
    final String loginName = "testUser";
    final String password = "paLswOrdha17£@£sh";
    final String moduleName = "test-module";

    final String flag = "thisisaflag";

    final String flagWithSpaces = "\n" + flag + "\t";

    moduleService.create(moduleName).block().getId();
    moduleService.setStaticFlag(moduleName, flag).block();

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUserDisplayName", loginName, password)))
        .exchange()
        .expectStatus()
        .isCreated();

    String token =
        JsonPath.parse(
                new String(
                    webTestClient
                        .post()
                        .uri("/api/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            BodyInserters.fromPublisher(
                                Mono.just(
                                    "{\"userName\": \""
                                        + loginName
                                        + "\", \"password\": \""
                                        + password
                                        + "\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    final String endpoint = String.format("/api/v1/flag/submit/%s", moduleName);

    final BodyInserter<String, ReactiveHttpOutputMessage> submissionBody =
        BodyInserters.fromValue(flagWithSpaces);

    final Flux<Submission> moduleSubmissionFlux =
        webTestClient
            .post()
            .uri(endpoint)
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(submissionBody)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Submission.class)
            .getResponseBody();

    StepVerifier.create(moduleSubmissionFlux.map(Submission::isValid))
        // We expect the submission to be invalid
        .expectNext(false)
        // We're done
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() {
    testService.deleteAll().block();
  }
}
