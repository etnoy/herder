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

import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.exception.RngException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class KeyService {

  private final CryptoFactory cryptoFactory;

  private byte[] byteGenerator(final SecureRandom strongPRNG, final @Min(1) int numberOfBytes) {
    byte[] randomBytes = new byte[numberOfBytes];
    strongPRNG.nextBytes(randomBytes);
    return randomBytes;
  }

  public byte[] generateRandomBytes(final @Min(1) int numberOfBytes) {
    try {
      final SecureRandom prng = cryptoFactory.getPrng();
      return byteGenerator(prng, numberOfBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new RngException("Could not initialize PRNG", e);
    }
  }

  public String generateRandomString(final @Min(1) int numberOfBytes) {
    return new String(generateRandomBytes(numberOfBytes), StandardCharsets.US_ASCII);
  }
}
