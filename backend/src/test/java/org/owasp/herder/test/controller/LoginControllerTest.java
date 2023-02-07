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
package org.owasp.herder.test.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.AuthResponse;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.authentication.ImpersonationDto;
import org.owasp.herder.authentication.LoginController;
import org.owasp.herder.authentication.LoginResponse;
import org.owasp.herder.authentication.PasswordLoginDto;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginController unit tests")
class LoginControllerTest extends BaseTest {

  LoginController loginController;

  @Mock
  UserService userService;

  @Mock
  WebTokenService webTokenService;

  @Mock
  PasswordEncoder passwordEncoder;

  @Mock
  ControllerAuthentication controllerAuthentication;

  @Test
  @DisplayName("Can error when logging in with bad credentials")
  void login_InvalidCredentials_Errors() {
    final String badCredentials = "Invalid username or password";
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );
    final LoginResponse loginResponse = LoginResponse.builder().errorMessage(badCredentials).build();

    final ResponseEntity<LoginResponse> badCredentialsResponse = new ResponseEntity<>(
      loginResponse,
      HttpStatus.UNAUTHORIZED
    );

    when(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .thenReturn(Mono.error(new BadCredentialsException(badCredentials)));
    StepVerifier.create(loginController.login(passwordLoginDto)).expectNext(badCredentialsResponse).verifyComplete();
  }

  @Test
  @DisplayName("Can return token when logging in")
  void login_ValidCredentials_ReturnsToken() {
    final PasswordLoginDto passwordLoginDto = new PasswordLoginDto(
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );
    final String mockJwt = "token";
    final Boolean userIsAdmin = false;
    final AuthResponse mockAuthResponse = AuthResponse
      .builder()
      .isAdmin(userIsAdmin)
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .userId(TestConstants.TEST_USER_ID)
      .build();
    final LoginResponse loginResponse = LoginResponse
      .builder()
      .id(TestConstants.TEST_USER_ID)
      .accessToken(mockJwt)
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .build();
    final ResponseEntity<LoginResponse> tokenResponse = new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .thenReturn(Mono.just(mockAuthResponse));
    when(webTokenService.generateToken(TestConstants.TEST_USER_ID, userIsAdmin)).thenReturn(mockJwt);

    StepVerifier.create(loginController.login(passwordLoginDto)).expectNext(tokenResponse).verifyComplete();
  }

  @Test
  @DisplayName("Can return impersonation token when impersonating")
  void impersonate_ValidCredentials_ReturnsToken() {
    final ImpersonationDto impersonationDto = new ImpersonationDto(TestConstants.TEST_USER_ID);
    final String impersonationToken = "token";

    final LoginResponse loginResponse = LoginResponse
      .builder()
      .id(TestConstants.TEST_USER_ID)
      .accessToken(impersonationToken)
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .build();
    final ResponseEntity<LoginResponse> tokenResponse = new ResponseEntity<>(loginResponse, HttpStatus.OK);

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID2));

    when(userService.getById(TestConstants.TEST_USER_ID))
      .thenReturn(
        Mono.just(
          TestConstants.TEST_USER_ENTITY
            .withId(TestConstants.TEST_USER_ID)
            .withDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)
        )
      );

    when(webTokenService.generateImpersonationToken(TestConstants.TEST_USER_ID2, TestConstants.TEST_USER_ID, false))
      .thenReturn(impersonationToken);

    StepVerifier.create(loginController.impersonate(impersonationDto)).expectNext(tokenResponse).verifyComplete();
  }

  @Test
  @DisplayName("Can register a new user")
  void register_ValidData_NoError() {
    final PasswordRegistrationDto passwordRegistrationDto = new PasswordRegistrationDto(
      TestConstants.TEST_USER_DISPLAY_NAME,
      TestConstants.TEST_USER_LOGIN_NAME,
      TestConstants.TEST_USER_PASSWORD
    );

    when(passwordEncoder.encode(TestConstants.TEST_USER_PASSWORD)).thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(userService.createPasswordUser(any(), any(), any())).thenReturn(Mono.just(TestConstants.TEST_USER_ID));
    StepVerifier
      .create(loginController.register(passwordRegistrationDto))
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    loginController = new LoginController(userService, webTokenService, passwordEncoder, controllerAuthentication);
  }
}
