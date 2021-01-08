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
package org.owasp.herder.authentication;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Clock;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.util.Date;
import org.springframework.stereotype.Service;

@Service
public class WebTokenService {

  private static final long EXPIRATION_TIME = 900;

  public static final Key JWT_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS512);

  private Clock clock;

  public WebTokenService() {
    resetClock();
  }

  public void resetClock() {
    this.clock = new WebTokenClock();
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  private Claims getAllClaimsFromToken(String token) {
    return Jwts.parserBuilder()
        .setClock(clock)
        .setSigningKey(JWT_KEY)
        .build()
        .parseClaimsJws(token)
        .getBody();
  }

  public long getUserIdFromToken(final String token) {
    return Long.parseLong(getAllClaimsFromToken(token).getSubject());
  }

  private Date getExpirationDateFromToken(final String token) {
    return getAllClaimsFromToken(token).getExpiration();
  }

  private boolean isTokenExpired(final String token) {
    try {
      final Date expiration = getExpirationDateFromToken(token);
      return expiration.before(clock.now());
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  public String generateToken(final long userId) {
    // TODO: use clock durations here instead
    final Date creationTime = clock.now();
    final Date expirationTime = new Date(clock.now().getTime() + 1000 * EXPIRATION_TIME);

    return Jwts.builder()
        .setSubject(Long.toString(userId))
        .setIssuedAt(creationTime)
        .setExpiration(expirationTime)
        .signWith(JWT_KEY)
        .compact();
  }

  public boolean validateToken(String token) {
    try {
      return !isTokenExpired(token);
    } catch (SignatureException e) {
      return false;
    }
  }
}
