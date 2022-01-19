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

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.AuthResponse;
import org.owasp.herder.authentication.LoginController;
import org.owasp.herder.authentication.LoginResponse;
import org.owasp.herder.authentication.PasswordLoginDto;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginController unit tests")
class LoginControllerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  LoginController loginController;

  @Mock UserService userService;

  @Mock WebTokenService webTokenService;

  @Mock PasswordEncoder passwordEncoder;

  @Test
  void login_InvalidCredentials_Returns401() {
    final String userName = "user";
    final String password = "password";
    final String badCredentials = "Invalid username or password";
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(userName, password);
    final LoginResponse loginResponse =
        LoginResponse.builder().errorMessage(badCredentials).build();

    final ResponseEntity<LoginResponse> badCredentialsResponse =
        new ResponseEntity<>(loginResponse, HttpStatus.UNAUTHORIZED);

    when(userService.authenticate(userName, password))
        .thenReturn(Mono.error(new BadCredentialsException(badCredentials)));
    StepVerifier.create(loginController.login(passwordLoginDto))
        .expectNext(badCredentialsResponse)
        .expectComplete()
        .verify();
  }

  @Test
  void login_ValidCredentials_ReturnsJWT() {
    final String userName = "user";
    final String password = "password";
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(userName, password);
    final String mockJwt = "token";
    final Boolean mockUserIsAdmin = false;
    final long mockUserId = 122L;
    final AuthResponse mockAuthResponse =
        AuthResponse.builder()
            .isAdmin(mockUserIsAdmin)
            .userName(userName)
            .userId(mockUserId)
            .build();
    final LoginResponse loginResponse = LoginResponse.builder().accessToken(mockJwt).build();
    final ResponseEntity<LoginResponse> tokenResponse =
        new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(userService.authenticate(userName, password)).thenReturn(Mono.just(mockAuthResponse));
    when(webTokenService.generateToken(mockUserId, mockUserIsAdmin)).thenReturn(mockJwt);
    StepVerifier.create(loginController.login(passwordLoginDto))
        .expectNext(tokenResponse)
        .expectComplete()
        .verify();
  }

  @Test
  void register_ValidData_ReturnsUserid() {
    final String displayName = "displayName";
    final String userName = "user";
    final String password = "password";
    final String encodedPassword = "encoded";
    final PasswordRegistrationDto passwordRegistrationDto =
        new PasswordRegistrationDto(displayName, userName, password);
    final long mockUserId = 255L;
    when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
    when(userService.createPasswordUser(displayName, userName, encodedPassword))
        .thenReturn(Mono.just(mockUserId));
    StepVerifier.create(loginController.register(passwordRegistrationDto))
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    loginController = new LoginController(userService, webTokenService, passwordEncoder);
  }
}
