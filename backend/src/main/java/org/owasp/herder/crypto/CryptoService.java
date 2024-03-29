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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.exception.CryptographicException;
import org.owasp.herder.validation.ValidKey;
import org.owasp.herder.validation.ValidMessage;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public final class CryptoService {

  private final CryptoFactory cryptoFactory;

  public byte[] hmac(final @ValidKey byte[] key, final @ValidMessage byte[] message) {
    final Mac hmac;

    try {
      hmac = cryptoFactory.getHmac();
    } catch (NoSuchAlgorithmException e) {
      throw new CryptographicException("Could not initialize MAC algorithm", e);
    }

    SecretKeySpec secretKeySpec = cryptoFactory.getSecretKeySpec(key);

    try {
      hmac.init(secretKeySpec);
    } catch (InvalidKeyException e) {
      throw new CryptographicException("Invalid key supplied to MAC", e);
    }

    return hmac.doFinal(message);
  }
}
