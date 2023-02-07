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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.jsonwebtoken.security.SignatureException;
import java.security.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.test.util.TestConstants;

@DisplayName("WebTokenKeyManager unit tests")
class WebTokenKeyManagerTest {

  WebTokenKeyManager webTokenKeyManager;

  @Test
  @DisplayName("Can invalidate access token for user")
  void invalidateAccessToken_ValidUserId_Succeeds() {
    assertDoesNotThrow(() -> webTokenKeyManager.invalidateAccessToken(TestConstants.TEST_USER_ID));
  }

  @Test
  @DisplayName("Can get key for user")
  void getKeyForUser_UserHasKey_ReturnsKey() {
    webTokenKeyManager.generateUserKey(TestConstants.TEST_USER_ID);

    final Key userKey = webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID);

    assertThat(userKey).isNotNull();
    assertThat(userKey.getAlgorithm()).isEqualTo("HmacSHA512");
  }

  @Test
  @DisplayName("Can error when getting key for user without key")
  void getKeyForUser_UserWithoutKey_Errors() {
    assertThatThrownBy(() -> webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID))
      .isInstanceOf(SignatureException.class)
      .hasMessage("Signing key is not registred for the subject");
  }

  @Test
  @DisplayName("Can clear all keys")
  void clearAllKeys_UserWithoutKey_Errors() {
    webTokenKeyManager.generateUserKey(TestConstants.TEST_USER_ID);
    webTokenKeyManager.clearAllKeys();

    assertThatThrownBy(() -> webTokenKeyManager.getKeyForUser(TestConstants.TEST_USER_ID))
      .isInstanceOf(SignatureException.class)
      .hasMessage("Signing key is not registred for the subject");
  }

  @Test
  @DisplayName("Can get key for user with getOrGenerateKeyForUser")
  void getOrGenerateKeyForUser_UserHasKey_ReturnsKey() {
    webTokenKeyManager.generateUserKey(TestConstants.TEST_USER_ID);

    final Key userKey = webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID);

    assertThat(userKey).isNotNull();
    assertThat(userKey.getAlgorithm()).isEqualTo("HmacSHA512");
  }

  @Test
  @DisplayName("Can generate key for user without key")
  void getOrGenerateKeyForUser_UserWithoutKey_GeneratesAndReturnsKey() {
    final Key userKey = webTokenKeyManager.getOrGenerateKeyForUser(TestConstants.TEST_USER_ID);

    assertThat(userKey).isNotNull();
    assertThat(userKey.getAlgorithm()).isEqualTo("HmacSHA512");
  }

  @Test
  @DisplayName("Can generate new key for user")
  void generateUserKey_UserHasKey_GeneratesNewKey() {
    final Key oldKey = webTokenKeyManager.generateUserKey(TestConstants.TEST_USER_ID);
    final Key newKey = webTokenKeyManager.generateUserKey(TestConstants.TEST_USER_ID);

    assertThat(newKey).isNotEqualTo(oldKey);
    assertThat(newKey.getAlgorithm()).isEqualTo("HmacSHA512");
  }

  @BeforeEach
  void setup() {
    webTokenKeyManager = new WebTokenKeyManager();
  }
}
