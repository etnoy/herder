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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.WebTokenClock;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebTokenService unit tests")
class WebTokenServiceTest {

  final Key testKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

  private WebTokenService webTokenService;

  @Mock
  WebTokenKeyManager webTokenKeyManager;

  @Mock
  WebTokenClock webTokenClock;

  @Test
  @DisplayName("Can error when parsing an expired token")
  void generateToken_TokenExpired_TokenInvalid() {
    setClock(TestConstants.YEAR_2000_CLOCK);

    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    final String token = webTokenService.generateToken(TestConstants.TEST_USER_ID, true);

    setClock(TestConstants.YEAR_2100_CLOCK);

    final JwtParser jwtParser = Jwts.parserBuilder().setClock(webTokenClock).setSigningKey(testKey).build();

    assertThatThrownBy(() -> {
        jwtParser.parseClaimsJws(token);
      })
      .isInstanceOf(JwtException.class)
      .hasMessageContaining("JWT expired at 2000-01-01T10:15:00Z. Current time: 2100-01-01T10:00:00Z");
  }

  @Test
  @DisplayName("Can parse a valid admin token")
  void generateToken_TokenExpiresInFutureAndRoleIsAdmin_ValidAdminToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);
    setClock(TestConstants.YEAR_2000_CLOCK);

    final String token = webTokenService.generateToken(TestConstants.TEST_USER_ID, true);
    final JwtParser jwtParser = Jwts
      .parserBuilder()
      .setSigningKey(testKey)
      .setClock(TestConstants.year2000WebTokenClock)
      .build();

    assertThat(jwtParser.parseClaimsJws(token).getBody()).containsEntry("role", "admin");
  }

  @Test
  @DisplayName("Can parse a valid token")
  void generateToken_TokenExpiresInTheFutureAndRoleIsUser_Valid() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    setClock(TestConstants.YEAR_2100_CLOCK);

    final String token = webTokenService.generateToken(TestConstants.TEST_USER_ID, false);
    final JwtParser jwtParser = Jwts
      .parserBuilder()
      .setClock(webTokenClock)
      .setSigningKey(testKey)
      .setClock(TestConstants.year2000WebTokenClock)
      .build();

    assertThat(jwtParser.parseClaimsJws(token).getBody()).containsEntry("role", "user");
  }

  @Test
  @DisplayName("Can generate an admin token")
  void generateToken_ValidUserIdIsAdmin_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    setClock(TestConstants.YEAR_2000_CLOCK);

    final String token = webTokenService.generateToken(TestConstants.TEST_USER_ID, true);
    final Claims claims = Jwts
      .parserBuilder()
      .setClock(webTokenClock)
      .setSigningKey(testKey)
      .build()
      .parseClaimsJws(token)
      .getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.get("role", String.class)).isEqualTo("admin");
    assertThat(claims.getSubject()).isEqualTo(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can generate a token")
  void generateToken_ValidUserIdNotAdmin_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    resetClock();
    final String token = webTokenService.generateToken(TestConstants.TEST_USER_ID);
    final Claims claims = Jwts.parserBuilder().setSigningKey(testKey).build().parseClaimsJws(token).getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims.get("role", String.class)).isEqualTo("user");
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.getSubject()).isEqualTo(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can generate an impersonation token")
  void generateImpersonationToken_ValidUserIdNotAdmin_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    resetClock();

    final String token = webTokenService.generateImpersonationToken(
      TestConstants.TEST_USER_ID,
      TestConstants.TEST_USER_ID2,
      false
    );

    final Claims claims = Jwts.parserBuilder().setSigningKey(testKey).build().parseClaimsJws(token).getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims).contains(entry("impersonator", TestConstants.TEST_USER_ID), entry("role", "user"));
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.getSubject()).isEqualTo(TestConstants.TEST_USER_ID2);
  }

  @Test
  @DisplayName("Can generate an admin impersonation token")
  void generateImpersonationToken_ValidAdminUserId_GeneratesValidToken() {
    when(webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    resetClock();

    final String token = webTokenService.generateImpersonationToken(
      TestConstants.TEST_USER_ID,
      TestConstants.TEST_USER_ID2,
      true
    );

    final Claims claims = Jwts.parserBuilder().setSigningKey(testKey).build().parseClaimsJws(token).getBody();

    assertThat(token.length()).isGreaterThan(10);
    assertThat(claims).contains(entry("impersonator", TestConstants.TEST_USER_ID), entry("role", "admin"));
    assertThat(claims.getIssuer()).isEqualTo("herder");
    assertThat(claims.getSubject()).isEqualTo(TestConstants.TEST_USER_ID2);
  }

  @Test
  @DisplayName("Can error when generating a token for an empty subject")
  void parseToken_EmptySubject_ThrowsBadCredentialsException() {
    final String testToken = Jwts
      .builder()
      .claim("role", "user")
      .setIssuer("herder")
      .setSubject("")
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512))
      .compact();

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid token");
  }

  @Test
  @DisplayName("Can error when generating a token for a null subject")
  void parseToken_NullUserId_ThrowsBadCredentialsException() {
    final String testToken = Jwts
      .builder()
      .claim("role", "user")
      .setIssuer("herder")
      .setSubject(null)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS512))
      .compact();

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid token");
  }

  @Test
  @DisplayName("Can error when generating a token with an invalid signing key")
  void parseToken_InvalidSigningKey_ThrowsBadCredentialsException() {
    final Key userKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID))
      .thenThrow(new SignatureException("Signing key is not registred for the subject"));

    final String testToken = Jwts
      .builder()
      .claim("role", "admin")
      .setIssuer("herder")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(userKey)
      .compact();

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid token");
  }

  @Test
  @DisplayName("Can error when generating a token for an invalid role")
  void parseToken_TokenWithInvalidRole_ThrowsBadCredentialsException() {
    final Key userKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    final String testToken = Jwts
      .builder()
      .claim("role", "bird")
      .setIssuer("herder")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(userKey)
      .compact();

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid token");
  }

  @Test
  @java.lang.SuppressWarnings("squid:S5838")
  @DisplayName("Can generate admin authorities when parsing an admin token")
  void parseToken_ValidAdminRoleToken_ReturnsAdminAuthority() {
    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    final String testToken = Jwts
      .builder()
      .claim("role", "admin")
      .setIssuer("herder")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(testKey)
      .compact();

    setClock(TestConstants.YEAR_2000_CLOCK);

    final Authentication authentication = webTokenService.parseToken(testToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(authentication.getCredentials()).isEqualTo(testToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER"))).isTrue();
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))).isTrue();
  }

  @Test
  @java.lang.SuppressWarnings("squid:S5838")
  @DisplayName("Can generate user authorities when parsing a token")
  void parseToken_ValidUserRoleToken_ReturnsUserAuthority() {
    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    final String testToken = Jwts
      .builder()
      .claim("role", "user")
      .setIssuer("herder")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(testKey)
      .compact();

    setClock(TestConstants.YEAR_2000_CLOCK);

    final Authentication authentication = webTokenService.parseToken(testToken);

    assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
    assertThat(authentication.getPrincipal()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(authentication.getCredentials()).isEqualTo(testToken);
    assertThat(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER"))).isTrue();
  }

  private void setClock(final Clock testClock) {
    when(webTokenClock.now()).thenReturn(Date.from(LocalDateTime.now(testClock).atZone(ZoneId.of("Z")).toInstant()));
  }

  @BeforeEach
  void setup() {
    webTokenService = new WebTokenService(webTokenKeyManager, webTokenClock);
  }

  @Test
  @DisplayName("Can error when parsing a token with an invalid role")
  void parseToken_InvalidRole_ThrowsBadCredentialsException() {
    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);

    final String testToken = Jwts
      .builder()
      .claim("role", "wolf")
      .setIssuer("herder")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(testKey)
      .compact();

    setClock(TestConstants.YEAR_2000_CLOCK);

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid role in token");
  }

  private void resetClock() {
    setClock(Clock.systemDefaultZone());
  }

  @Test
  @DisplayName("Can error when parsing a token with an invalid issuer")
  void parseToken_InvalidIssuer_ThrowsBadCredentialsException() {
    when(webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID)).thenReturn(testKey);
    setClock(TestConstants.YEAR_2000_CLOCK);
    final String testToken = Jwts
      .builder()
      .claim("role", "user")
      .setIssuer("wolf")
      .setSubject(TestConstants.TEST_USER_ID)
      .setIssuedAt(TestConstants.year2000WebTokenClock.now())
      .setExpiration(new Date(Long.MAX_VALUE))
      .signWith(testKey)
      .compact();

    assertThatThrownBy(() -> {
        webTokenService.parseToken(testToken);
      })
      .isInstanceOf(BadCredentialsException.class)
      .hasMessage("Invalid token");
  }
}
