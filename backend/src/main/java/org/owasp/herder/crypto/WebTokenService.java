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
package org.owasp.herder.crypto;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.authentication.Role;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Slf4j
@Validated
@RequiredArgsConstructor
public class WebTokenService {
  private final WebTokenKeyManager webTokenKeyManager;

  // How many seconds a JWT token should last before expiring
  private static final long EXPIRATION_TIME = 900;

  private final WebTokenClock webTokenClock;

  public long getExpirationTime() {
    return 1000 * EXPIRATION_TIME;
  }

  public String generateToken(@ValidUserId final String userId) {
    return generateToken(userId, false);
  }

  public String generateToken(@ValidUserId final String userId, final boolean isAdmin) {
    final Date creationTime = webTokenClock.now();
    final Date expirationTime = new Date(creationTime.getTime() + getExpirationTime());
    final Key userKey = webTokenKeyManager.getOrGenerateKeyForUser(userId);

    String role;

    if (isAdmin) {
      role = "admin";
    } else {
      role = "user";
    }

    return Jwts.builder()
        .claim("role", role)
        .setIssuer("herder")
        .setSubject(userId)
        .setIssuedAt(creationTime)
        .setExpiration(expirationTime)
        .signWith(userKey)
        .compact();
  }

  public Authentication parseToken(@NotNull @NotEmpty String token) throws AuthenticationException {
    final Claims parsedClaims;

    try {
      parsedClaims =
          Jwts.parserBuilder()
              .setSigningKeyResolver(
                  new SigningKeyResolverAdapter() {
                    @Override
                    public Key resolveSigningKey(
                        @SuppressWarnings("rawtypes") JwsHeader header, Claims claims) {
                      final String userIdString = claims.getSubject();

                      if (userIdString == null || userIdString.isEmpty())
                        throw new MissingClaimException(
                            header, claims, "Subject is not provided in token");

                      return webTokenKeyManager.getKeyForUser(userIdString);
                    }
                  })
              .requireIssuer("herder")
              .setClock(webTokenClock)
              .build()
              .parseClaimsJws(token)
              .getBody();
    } catch (JwtException e) {
      // Invalid token
      log.debug("Invalid token encountered. Reason: " + e.getMessage());
      throw new BadCredentialsException("Invalid token", e);
    }

    final String userIdErrorMessage =
        "Invalid userid " + parsedClaims.getSubject() + " found in token";

    String userId;
    try {
      userId = parsedClaims.getSubject();
    } catch (NumberFormatException e) {
      log.debug(userIdErrorMessage);
      throw new BadCredentialsException(userIdErrorMessage, e);
    }

    if (userId.isEmpty()) {
      log.debug(userIdErrorMessage);
      throw new BadCredentialsException(userIdErrorMessage);
    }

    final String role = parsedClaims.get("role", String.class);

    ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();

    if (role.equals("admin")) {
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_ADMIN.name()));
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
    } else if (role.equals("user")) {
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
    } else {
      // Invalid role found, bail out
      throw new BadCredentialsException("Invalid role in token");
    }

    return new UsernamePasswordAuthenticationToken(userId, token, authorities);
  }
}
