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
package org.owasp.herder.test.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoFactory;
import org.owasp.herder.test.BaseTest;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoFactory unit tests")
class CryptoFactoryTest extends BaseTest {

  CryptoFactory cryptoFactory;

  @Test
  void getPrng_ReturnsSecureRandomInstance() throws NoSuchAlgorithmException {
    assertThat(cryptoFactory.getPrng()).isInstanceOf(SecureRandom.class);
  }

  @Test
  void getHmac_ReturnsMacInstance() throws NoSuchAlgorithmException {
    assertThat(cryptoFactory.getHmac()).isInstanceOf(Mac.class);
  }

  @Test
  void getHmacKey_ValidKey_ReturnsMacInstance() {
    final byte[] key = { -91, -79, 67 };
    assertThat(cryptoFactory.getSecretKeySpec(key)).isInstanceOf(Key.class);
  }

  @Test
  void getHmacKey_NullKey_ThrowsIllegalArgumentException() {
    assertThatExceptionOfType(IllegalArgumentException.class)
      .isThrownBy(() -> cryptoFactory.getSecretKeySpec(null));
  }

  @BeforeEach
  void setup() {
    cryptoFactory = new CryptoFactory();
  }
}
