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
import org.owasp.herder.authentication.PasswordLoginDto;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.authentication.WebTokenService;
import org.owasp.herder.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginController unit test")
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
  void login_InvalidCredentials_ReturnsJWT() {
    final String userName = "user";
    final String password = "password";
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(userName, password);
    final long mockUserId = 122L;
    when(userService.findUserIdByLoginName(userName)).thenReturn(Mono.just(mockUserId));
    when(userService.authenticate(userName, password)).thenReturn(Mono.just(false));
    StepVerifier.create(loginController.login(passwordLoginDto))
        .expectNext(new ResponseEntity<>(HttpStatus.UNAUTHORIZED))
        .expectComplete()
        .verify();
  }

  @Test
  void login_ValidCredentials_ReturnsJWT() {
    final String userName = "user";
    final String password = "password";
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(userName, password);
    final String mockJwt = "token";
    final long mockUserId = 122L;
    final AuthResponse mockAuthResponse = new AuthResponse(mockJwt, userName);
    when(userService.findUserIdByLoginName(userName)).thenReturn(Mono.just(mockUserId));
    when(userService.authenticate(userName, password)).thenReturn(Mono.just(true));
    when(webTokenService.generateToken(mockUserId)).thenReturn(mockJwt);
    StepVerifier.create(loginController.login(passwordLoginDto))
        .expectNext(new ResponseEntity<>(mockAuthResponse, HttpStatus.OK))
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
        .expectNext(mockUserId)
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    loginController = new LoginController(userService, webTokenService, passwordEncoder);
  }
}
