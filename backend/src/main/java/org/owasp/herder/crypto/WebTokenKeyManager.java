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

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Slf4j
public class WebTokenKeyManager {

  private final Map<String, Key> keys = new HashMap<>();

  public Key getOrGenerateKeyForUser(@ValidUserId final String userId) {
    if (!keys.containsKey(userId)) {
      // No key found, generate new key for user and store it
      return generateUserKey(userId);
    } else {
      return keys.get(userId);
    }
  }

  public Key getKeyForUser(@ValidUserId final String userId) {
    if (!keys.containsKey(userId)) {
      // No key found, throw error
      throw new SignatureException("Signing key is not registred for the subject");
    } else {
      return keys.get(userId);
    }
  }

  public void clearAllKeys() {
    keys.clear();
  }

  public Key generateUserKey(@ValidUserId final String userId) {
    log.debug("Generating new web token key for user with id " + userId);

    final Key userKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
    keys.put(userId, userKey);

    return userKey;
  }

  public void invalidateAccessToken(@ValidUserId final String userId) {
    log.debug("Invalidating web token for user with id " + userId);

    keys.remove(userId);
  }
}
