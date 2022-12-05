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
package org.owasp.herder.test.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.AuthenticationManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.user.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationManager unit tests")
class AuthenticationManagerTest {

  private AuthenticationManager authenticationManager;

  @Mock
  private WebTokenService webTokenService;

  @Mock
  private UserService userService;

  @Test
  void authenticate_ValidToken_Authenticates() {
    final Authentication mockAuthentication = mock(Authentication.class);
    final String mockToken = "token";
    when(mockAuthentication.getCredentials()).thenReturn(mockToken);

    when(webTokenService.parseToken(mockToken)).thenReturn(mockAuthentication);

    StepVerifier
      .create(authenticationManager.authenticate(mockAuthentication))
      .expectNext(mockAuthentication)
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    authenticationManager = new AuthenticationManager(webTokenService);
  }

  @Test
  void authenticate_InvalidToken_DoesNotAuthenticate() {
    final Authentication mockAuthentication = mock(Authentication.class);
    final String mockToken = "token";
    final String invalidToken = "Invalid token";
    when(mockAuthentication.getCredentials()).thenReturn(mockToken);

    when(webTokenService.parseToken(mockToken))
      .thenThrow(new BadCredentialsException(invalidToken));

    StepVerifier
      .create(authenticationManager.authenticate(mockAuthentication))
      .expectErrorMatches(throwable ->
        throwable instanceof BadCredentialsException &&
        throwable.getMessage().equals(invalidToken)
      )
      .verify();
  }
}
