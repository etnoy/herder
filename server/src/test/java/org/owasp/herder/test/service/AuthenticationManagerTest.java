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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.AuthenticationManager;
import org.owasp.herder.authentication.WebTokenService;
import org.owasp.herder.user.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationManager unit test")
class AuthenticationManagerTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  AuthenticationManager authenticationManager;

  @Mock WebTokenService webTokenService;

  @Mock UserService userService;

  @Test
  void authenticate_InvalidAuthentication_ReturnsValidAuthentication() {
    final Authentication mockAuthentication = mock(Authentication.class);
    final String mockToken = "token";
    when(mockAuthentication.getCredentials()).thenReturn(mockToken);
    when(webTokenService.validateToken(mockToken)).thenReturn(false);

    StepVerifier.create(authenticationManager.authenticate(mockAuthentication))
        .expectComplete()
        .verify();
  }

  @Test
  void authenticate_ValidAuthentication_ReturnsValidAuthentication() {
    final Authentication mockAuthentication = mock(Authentication.class);
    final String mockToken = "token";
    final long mockUserId = 548;
    when(mockAuthentication.getCredentials()).thenReturn(mockToken);
    when(webTokenService.validateToken(mockToken)).thenReturn(true);
    when(webTokenService.getUserIdFromToken(mockToken)).thenReturn(mockUserId);
    final SimpleGrantedAuthority mockSimpleGrantedAuthority1 = mock(SimpleGrantedAuthority.class);
    final SimpleGrantedAuthority mockSimpleGrantedAuthority2 = mock(SimpleGrantedAuthority.class);
    final SimpleGrantedAuthority mockSimpleGrantedAuthority3 = mock(SimpleGrantedAuthority.class);

    final Flux<SimpleGrantedAuthority> authorityFlux =
        Flux.just(
            mockSimpleGrantedAuthority1, mockSimpleGrantedAuthority2, mockSimpleGrantedAuthority3);
    final List<SimpleGrantedAuthority> authorities =
        Stream.of(
                mockSimpleGrantedAuthority1,
                mockSimpleGrantedAuthority2,
                mockSimpleGrantedAuthority3)
            .collect(Collectors.toList());
    when(userService.getAuthoritiesByUserId(mockUserId)).thenReturn(authorityFlux);
    StepVerifier.create(authenticationManager.authenticate(mockAuthentication))
        .assertNext(
            auth -> {
              assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
              assertThat(auth.getPrincipal()).isEqualTo(mockUserId);
              assertThat(auth.getAuthorities()).isEqualTo(authorities);
            })
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    authenticationManager = new AuthenticationManager(webTokenService, userService);
  }
}
