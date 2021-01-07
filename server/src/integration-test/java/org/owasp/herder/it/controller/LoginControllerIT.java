/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("LoginController integration test")
class LoginControllerIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired private WebTestClient webTestClient;

  @Autowired TestUtils testService;

  @Test
  @DisplayName("Logging in with correct credentials should return a valid token")
  void login_CorrectCredentials_ReturnsToken() {
    final String loginName = "test";
    final String hashedPassword = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

    final long createdUserId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    final String jws =
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
                                        + "\", \"password\": \"test\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody()))
            .read("$.token");

    final int signatureDotPosition = jws.lastIndexOf('.');

    final String jwt = jws.substring(0, signatureDotPosition);

    final int bodyDotPosition = jwt.lastIndexOf('.');

    final DocumentContext jsonBody =
        JsonPath.parse(new String(Base64.getDecoder().decode(jwt.substring(bodyDotPosition + 1))));

    final long userId = Long.parseLong(jsonBody.read("$.sub"));

    assertThat(userId).isEqualTo(createdUserId);
  }

  @Test
  @DisplayName("Logging in with an incorrect password should return HTTP Unauthorized")
  void login_WrongPassword_ReturnsUnauthorized() {
    final String loginName = "test";
    final String hashedPassword = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

    userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    webTestClient
        .post()
        .uri("/api/v1/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromPublisher(
                Mono.just("{\"userName\": \"" + loginName + "\", \"password\": \"wrong\"}"),
                String.class))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  @DisplayName("Logging in with an incorrect username should return HTTP Unauthorized")
  void login_WrongUserName_ReturnsUnauthorized() {

    final String loginName = "test";
    final String hashedPassword = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

    userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    webTestClient
        .post()
        .uri("/api/v1/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromPublisher(
                Mono.just("{\"userName\": \"doesnotexist\", \"password\": \"wrong\"}"),
                String.class))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @BeforeEach
  private void setUp() {
    testService.deleteAll().block();
  }
}
