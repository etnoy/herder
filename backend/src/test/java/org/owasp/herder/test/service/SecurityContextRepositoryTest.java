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
package org.owasp.herder.test.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.AuthenticationManager;
import org.owasp.herder.authentication.SecurityContextRepository;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityContextRepository unit tests")
class SecurityContextRepositoryTest extends BaseTest {

  private SecurityContextRepository securityContextRepository;

  @Mock
  AuthenticationManager authenticationManager;

  @Test
  @DisplayName("Can return empty authentication when loading security context with invalid header")
  void load_InvalidHeader_ReturnsEmptyAuthentication() {
    final ServerWebExchange mockServerWebExchange = mock(ServerWebExchange.class);
    final String token = "authToken";
    final ServerHttpRequest mockServerHttpRequest = mock(ServerHttpRequest.class);
    when(mockServerWebExchange.getRequest()).thenReturn(mockServerHttpRequest);
    final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
    when(mockServerHttpRequest.getHeaders()).thenReturn(mockHttpHeaders);
    final String mockAuthorizationHeader = "Hello World" + token;
    when(mockHttpHeaders.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(mockAuthorizationHeader);
    StepVerifier
      .create(securityContextRepository.load(mockServerWebExchange).map(SecurityContext::getAuthentication))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return empty authentication when loading security context with null")
  void load_NullHeader_ReturnsSecurityContext() {
    final ServerWebExchange mockServerWebExchange = mock(ServerWebExchange.class);

    final ServerHttpRequest mockServerHttpRequest = mock(ServerHttpRequest.class);
    when(mockServerWebExchange.getRequest()).thenReturn(mockServerHttpRequest);
    final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
    when(mockServerHttpRequest.getHeaders()).thenReturn(mockHttpHeaders);
    when(mockHttpHeaders.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(null);

    StepVerifier
      .create(securityContextRepository.load(mockServerWebExchange).map(SecurityContext::getAuthentication))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can load security context")
  void load_ValidHeader_ReturnsSecurityContext() {
    final ServerWebExchange mockServerWebExchange = mock(ServerWebExchange.class);

    final String token = "authToken";

    final ServerHttpRequest mockServerHttpRequest = mock(ServerHttpRequest.class);
    when(mockServerWebExchange.getRequest()).thenReturn(mockServerHttpRequest);
    final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
    when(mockServerHttpRequest.getHeaders()).thenReturn(mockHttpHeaders);
    final String mockAuthorizationHeader = "Bearer " + token;
    when(mockHttpHeaders.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(mockAuthorizationHeader);

    final List<SimpleGrantedAuthority> mockAuthorities = Arrays.asList(
      new SimpleGrantedAuthority[] { new SimpleGrantedAuthority("ROLE_USER") }
    );

    final Authentication mockAuthentication = new UsernamePasswordAuthenticationToken(
      TestConstants.TEST_USER_ID,
      token,
      mockAuthorities
    );

    when(authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(null, token)))
      .thenReturn(Mono.just(mockAuthentication));

    StepVerifier
      .create(securityContextRepository.load(mockServerWebExchange).map(SecurityContext::getAuthentication))
      .expectNext(mockAuthentication)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error if authentication fails when loading security context")
  void load_FailedAuthentication_ReturnsForbidden() {
    final ServerWebExchange mockServerWebExchange = mock(ServerWebExchange.class);

    final String token = "authToken";

    final ServerHttpRequest mockServerHttpRequest = mock(ServerHttpRequest.class);
    when(mockServerWebExchange.getRequest()).thenReturn(mockServerHttpRequest);
    final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
    when(mockServerHttpRequest.getHeaders()).thenReturn(mockHttpHeaders);
    final String mockAuthorizationHeader = "Bearer " + token;
    when(mockHttpHeaders.getFirst(HttpHeaders.AUTHORIZATION)).thenReturn(mockAuthorizationHeader);

    when(authenticationManager.authenticate(any()))
      .thenReturn(Mono.error(new BadCredentialsException("Wrong password")));

    StepVerifier
      .create(securityContextRepository.load(mockServerWebExchange))
      .expectErrorMatches(throwable ->
        throwable instanceof ResponseStatusException && throwable.getMessage().contains("Wrong password")
      )
      .verify();
  }

  @Test
  @DisplayName("Can return Not Implemented wieh saving security context")
  void save_NotImplemented() {
    StepVerifier
      .create(securityContextRepository.save(null, null))
      .expectError(UnsupportedOperationException.class)
      .verify();
  }

  @BeforeEach
  void setup() {
    securityContextRepository = new SecurityContextRepository(authenticationManager);
  }
}
