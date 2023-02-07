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
package org.owasp.herder.crypto;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
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

  private static final String IMPERSONATOR_CLAIM = "impersonator";

  private static final String ADMIN_ROLE = "admin";

  private static final String USER_ROLE = "user";

  private static final String ROLE_CLAIM = "role";

  private static final String HERDER_ISSUER = "herder";

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
      role = ADMIN_ROLE;
    } else {
      role = USER_ROLE;
    }

    return Jwts
      .builder()
      .claim(ROLE_CLAIM, role)
      .setIssuer(HERDER_ISSUER)
      .setSubject(userId)
      .setIssuedAt(creationTime)
      .setExpiration(expirationTime)
      .signWith(userKey)
      .compact();
  }

  public String generateImpersonationToken(
    @ValidUserId final String impersonatorUserId,
    @ValidUserId final String impersonatedUserId,
    final boolean impersonateAnAdmin
  ) {
    final Date creationTime = webTokenClock.now();
    final Date expirationTime = new Date(creationTime.getTime() + getExpirationTime());
    final Key userKey = webTokenKeyManager.getOrGenerateKeyForUser(impersonatorUserId);

    String role = USER_ROLE;

    if (impersonateAnAdmin) {
      role = ADMIN_ROLE;
    }

    return Jwts
      .builder()
      .claim(ROLE_CLAIM, role)
      .claim(IMPERSONATOR_CLAIM, impersonatorUserId)
      .setIssuer(HERDER_ISSUER)
      .setSubject(impersonatedUserId)
      .setIssuedAt(creationTime)
      .setExpiration(expirationTime)
      .signWith(userKey)
      .compact();
  }

  public Authentication parseToken(@NotNull @NotEmpty String token) throws AuthenticationException {
    final Claims parsedClaims;
    try {
      parsedClaims =
        Jwts
          .parserBuilder()
          .setSigningKeyResolver(
            new SigningKeyResolverAdapter() {
              @Override
              public Key resolveSigningKey(@SuppressWarnings("rawtypes") JwsHeader header, Claims claims) {
                String subjectId;

                if (claims.containsKey(IMPERSONATOR_CLAIM)) {
                  subjectId = claims.get(IMPERSONATOR_CLAIM, String.class);
                } else {
                  subjectId = claims.getSubject();
                }
                if (subjectId == null || subjectId.isEmpty()) throw new MissingClaimException(
                  header,
                  claims,
                  "Subject is not provided in token"
                );
                return webTokenKeyManager.getKeyForUser(subjectId);
              }
            }
          )
          .requireIssuer(HERDER_ISSUER)
          .setClock(webTokenClock)
          .build()
          .parseClaimsJws(token)
          .getBody();
    } catch (JwtException e) {
      // Invalid token
      log.debug("Invalid token encountered. Reason: " + e.getMessage());
      throw new BadCredentialsException("Invalid token", e);
    }

    final String userId = parsedClaims.getSubject();

    final String role = parsedClaims.get(ROLE_CLAIM, String.class);

    ArrayList<SimpleGrantedAuthority> authorities = new ArrayList<>();

    if (role.equals(ADMIN_ROLE)) {
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_ADMIN.name()));
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
    } else if (role.equals(USER_ROLE)) {
      authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER.name()));
    } else {
      // Invalid role found, bail out
      throw new BadCredentialsException("Invalid role in token");
    }

    return new UsernamePasswordAuthenticationToken(userId, token, authorities);
  }
}
