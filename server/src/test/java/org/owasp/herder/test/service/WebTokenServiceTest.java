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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.security.Key;
import java.time.Clock;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebTokenService unit tests")
class WebTokenServiceTest {

  final Long testUserId = 843L;

  final Key testKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

  private WebTokenService webTokenService;

  @Mock WebTokenKeyManager webTokenKeyManager;

  @Test
  void generateToken_TokenExpired_TokenInvalid() {

    setClock(TestConstants.longAgoClock);

    when(webTokenKeyManager.getOrGenerateKeyForUser(testUserId)).thenReturn(testKey);

    final String token = webTokenService.generateToken(testUserId, true);

    final JwtParser jwtParser =
        Jwts.parserBuilder()
            .setClock(TestConstants.year2100WebTokenClock)
            .setSigningKey(testKey)
            .build();

    assertThatThrownBy(
            () -> {
              jwtParser.parseClaimsJws(token);
            })
        .isInstanceOf(JwtException.class)
        .hasMessageContaining(
            "JWT expired at 2000-01-01T10:15:00Z. Current time: 2100-01-01T10:00:00Z");
  }

  @Test
  void generateToken_TokenExpiresInFutureAndRoleIsAdmin_ValidAdminToken() {

    when(webTokenKeyManager.getOrGenerateKeyForUser(testUserId)).thenReturn(testKey);

    setClock(TestConstants.year2100Clock);

    final String token = webTokenService.generateToken(testUserId, true);
    setClock(TestConstants.longAgoClock);
    final JwtParser jwtParser =
        Jwts.parserBuilder()
            .setSigningKey(testKey)
            .setClock(TestConstants.year2000WebTokenClock)
            .build();

    assertThat(jwtParser.parseClaimsJws(token).getBody()).containsEntry("role", "admin");
  }

  @Test
  void generateToken_TokenExpiresInTheFutureAndRoleIsUser_Valid() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(testUserId)).thenReturn(testKey);

    setClock(TestConstants.year2100Clock);

    final String token = webTokenService.generateToken(testUserId, false);
    setClock(TestConstants.longAgoClock);
    final JwtParser jwtParser =
        Jwts.parserBuilder()
            .setSigningKey(testKey)
            .setClock(TestConstants.year2000WebTokenClock)
            .build();

    assertThat(jwtParser.parseClaimsJws(token).getBody()).containsEntry("role", "user");
  }

  @Test
  void generateToken_ValidUserIdIsAdmin_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(testUserId)).thenReturn(testKey);

    final String token = webTokenService.generateToken(testUserId, true);
    final Claims claims =
        Jwts.parserBuilder().setSigningKey(testKey).build().parseClaimsJws(token).getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.get("role", String.class)).isEqualTo("admin");
    assertThat(claims.getSubject()).isEqualTo(testUserId.toString());
  }

  @Test
  void generateToken_ValidUserIdNotAdmin_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(testUserId)).thenReturn(testKey);

    final String token = webTokenService.generateToken(testUserId, false);
    final Claims claims =
        Jwts.parserBuilder().setSigningKey(testKey).build().parseClaimsJws(token).getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims.get("role", String.class)).isEqualTo("user");
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.getSubject()).isEqualTo(testUserId.toString());
  }

  @Test
  void parseToken_EmptyUserId_ThrowsBadCredentialsException() {
    final String testToken =
        Jwts.builder()
            .claim("role", "user")
            .setIssuer("herder")
            .setSubject("")
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512))
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid token");
  }

  @Test
  void parseToken_NullUserId_ThrowsBadCredentialsException() {

    final String testToken =
        Jwts.builder()
            .claim("role", "user")
            .setIssuer("herder")
            .setSubject(null)
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512))
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid token");
  }

  @Test
  void parseToken_InvalidSigningKey_ThrowsBadCredentialsException() {

    final Key userKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    when(webTokenKeyManager.getKeyForUser(testUserId.toString()))
        .thenThrow(new SignatureException("Signing key is not registred for the subject"));

    final String testToken =
        Jwts.builder()
            .claim("role", "admin")
            .setIssuer("herder")
            .setSubject(testUserId.toString())
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(userKey)
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid token");
  }

  @Test
  void parseToken_TokenWithInvalidRole_ThrowsBadCredentialsException() {
    final Long testUserId = 349L;

    final Key userKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    when(webTokenKeyManager.getKeyForUser(testUserId.toString())).thenReturn(testKey);

    final String testToken =
        Jwts.builder()
            .claim("role", "bird")
            .setIssuer("herder")
            .setSubject(testUserId.toString())
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(userKey)
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid token");
  }

  @Test
  void parseToken_ValidAdminRoleToken_ReturnsAdminAuthority() {

    when(webTokenKeyManager.getKeyForUser(testUserId.toString())).thenReturn(testKey);

    final String testToken =
        Jwts.builder()
            .claim("role", "admin")
            .setIssuer("herder")
            .setSubject(testUserId.toString())
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(testKey)
            .compact();

    final Authentication authentication = webTokenService.parseToken(testToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(testUserId);
    assertThat(authentication.getCredentials()).isEqualTo(testToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  void parseToken_ValidUserRoleToken_ReturnsUserAuthority() {

    when(webTokenKeyManager.getKeyForUser(testUserId.toString())).thenReturn(testKey);

    final String testToken =
        Jwts.builder() //
            .claim("role", "user") //
            .setIssuer("herder") //
            .setSubject(testUserId.toString()) //
            .setIssuedAt(TestConstants.year2000WebTokenClock.now()) //
            .setExpiration(new Date(Long.MAX_VALUE)) //
            .signWith(testKey) //
            .compact();

    final Authentication authentication = webTokenService.parseToken(testToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(testUserId);
    assertThat(authentication.getCredentials()).isEqualTo(testToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
  }

  private void setClock(final Clock clock) {
    webTokenService.setClock(clock);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    webTokenService = new WebTokenService(webTokenKeyManager);
  }

  @Test
  void parseToken_InvalidRole_ThrowsBadCredentialsException() {
    when(webTokenKeyManager.getKeyForUser(testUserId.toString())).thenReturn(testKey);

    final String testToken =
        Jwts.builder()
            .claim("role", "wolf")
            .setIssuer("herder")
            .setSubject(testUserId.toString())
            .setIssuedAt(TestConstants.year2000WebTokenClock.now())
            .setExpiration(new Date(Long.MAX_VALUE))
            .signWith(testKey)
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid role in token");
  }

  @Test
  void parseToken_InvalidIssuer_ThrowsBadCredentialsException() {
    when(webTokenKeyManager.getKeyForUser(testUserId.toString())).thenReturn(testKey);

    final String testToken =
        Jwts.builder() //
            .claim("role", "user") //
            .setIssuer("wolf") //
            .setSubject(testUserId.toString()) //
            .setIssuedAt(TestConstants.year2000WebTokenClock.now()) //
            .setExpiration(new Date(Long.MAX_VALUE)) //
            .signWith(testKey) //
            .compact();

    assertThatThrownBy(
            () -> {
              webTokenService.parseToken(testToken);
            })
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid token");
  }
}
