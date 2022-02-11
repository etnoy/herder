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
package org.owasp.herder.it.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import com.jayway.jsonpath.JsonPath;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("UserController API integration tests")
class UserControllerApiIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired WebTestClient webTestClient;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @Test
  void getUserList_AuthenticatedUser_Forbidden() {
    final String loginName = "test";
    final String hashedPassword = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

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
                                        + "\", \"password\": \"test\"}"),
                                String.class))
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    webTestClient
        .get()
        .uri("/api/v1/users")
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void getUserList_UserPromotedToAdmin_Success() {
    final String loginName = "test";
    final String hashedPassword = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";

    final long userId =
        userService.createPasswordUser("Test User", loginName, hashedPassword).block();

    final String token =
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
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    webTestClient
        .get()
        .uri("/api/v1/users")
        .header("Authorization", "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isForbidden();

    // Promote user to admin
    userService.promote(userId).block();

    String newToken =
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
                        .expectBody()
                        .returnResult()
                        .getResponseBody()))
            .read("$.accessToken");

    // Now the user should be able to see user list
    webTestClient
        .get()
        .uri("/api/v1/users")
        .header("Authorization", "Bearer " + newToken)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void listUsers_UsersExist_ReturnsUserList() throws Exception {
    final String loginName = "test";
    final String password = "paLswOrdha17£@£sh";

    HashSet<Long> userIdSet = new HashSet<>();

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

    final long userId = userService.findUserIdByLoginName(loginName).block();

    // Promote this user to admin
    userService.promote(userId).block();

    userIdSet.add(userId);

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

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUser2", "loginName2", "paLswOrdha17£@£sh")))
        .exchange()
        .expectStatus()
        .isCreated();

    userIdSet.add(userService.findUserIdByLoginName("loginName2").block());

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                new PasswordRegistrationDto("TestUser3", "loginName3", "paLswOrdha17£@£sh")))
        .exchange()
        .expectStatus()
        .isCreated();

    userIdSet.add(userService.findUserIdByLoginName("loginName3").block());

    StepVerifier.create(
            webTestClient
                .get()
                .uri("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .returnResult(UserEntity.class)
                .getResponseBody()
                .map(UserEntity::getId))
        .recordWith(HashSet::new)
        .thenConsumeWhile(__ -> true)
        .expectRecordedMatches(x -> x.equals(userIdSet))
        .expectComplete()
        .verify();
  }

  @Test
  void register_ValidData_ReturnsValidUser() throws Exception {
    final String loginName = "test";
    final String password = "paLswOrdha17£@£sh";

    webTestClient
        .post()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromPublisher(
                Mono.just(
                    "{\"displayName\": \""
                        + loginName
                        + "\", \"userName\": \""
                        + loginName
                        + "\",  \"password\": \""
                        + password
                        + "\"}"),
                String.class))
        .exchange()
        .expectStatus()
        .isCreated();

    final long userId = userService.findUserIdByLoginName("test").block();

    // Promote this user to admin
    userService.promote(userId).block();

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

    FluxExchangeResult<UserEntity> getResult =
        webTestClient
            .get()
            .uri("/api/v1/user/" + Long.toString(userId))
            .header("Authorization", "Bearer " + token)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .returnResult(UserEntity.class);

    StepVerifier.create(getResult.getResponseBody())
        .assertNext(
            getData -> {
              assertThat(getData).isEqualTo(userService.findById(userId).block());
            })
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
  }
}
