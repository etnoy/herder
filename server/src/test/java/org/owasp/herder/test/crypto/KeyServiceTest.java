/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoFactory;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.RngException;
import reactor.core.publisher.Hooks;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyService unit test")
class KeyServiceTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  KeyService keyService;

  @Mock CryptoFactory prngFactory;

  @Test
  void byteFlagToString_ValidBytes_ReturnsString() {
    assertThat(
            keyService.bytesToHexString(
                new byte[] {116, 104, 105, 115, 105, 115, 97, 102, 108, 97, 103}))
        .isEqualTo("74686973697361666c6167");
  }

  @Test
  void convertStringKeyToBytes_ValidInput_ReturnsExpectedOutput() {
    assertThat(
            keyService.convertByteKeyToString(
                new byte[] {116, 104, 105, 115, 105, 115, 97, 102, 108, 97, 103}))
        .isEqualTo("thisisaflag");
  }

  @Test
  void generateRandomBytes_NoSuchAlgorithmException_ThrowsRngException() throws Exception {
    final int[] testedLengths = {0, 1, 12, 16, 128, 4096};

    when(prngFactory.getPrng())
        .thenThrow(
            new NoSuchAlgorithmException(
                "Null/empty securerandom.strongAlgorithms Security Property"));

    for (int length : testedLengths) {
      assertThatExceptionOfType(RngException.class)
          .isThrownBy(() -> keyService.generateRandomBytes(length))
          .withMessageMatching("Could not initialize PRNG");
    }
  }

  @Test
  void generateRandomBytes_ValidLength_ReturnsRandomBytes() throws Exception {
    final int[] testedLengths = {0, 1, 12, 16, 128, 4096};

    final SecureRandom mockPrng = mock(SecureRandom.class);

    when(prngFactory.getPrng()).thenReturn(mockPrng);

    for (int length : testedLengths) {
      final byte[] randomBytes = keyService.generateRandomBytes(length);
      assertThat(randomBytes).isNotNull();
      assertThat(randomBytes).hasSize(length);
    }
  }

  @Test
  void generateRandomString_ValidLength_ReturnsRandomString() throws Exception {
    final SecureRandom mockPrng = mock(SecureRandom.class);

    when(prngFactory.getPrng()).thenReturn(mockPrng);
    final int[] testedLengths = {0, 1, 12, 16, 128, 4096};
    for (int length : testedLengths) {
      final String randomString = keyService.generateRandomString(length);
      assertThat(randomString).isNotNull();
      assertThat(randomString).hasSize(length);
    }
  }

  @BeforeEach
  private void setUp() {
    keyService = new KeyService(prngFactory);
  }

  @Test
  void stringFlagToByte_ValidString_ReturnsString() throws DecoderException {
    assertThat(keyService.hexStringToBytes("74686973697361666c6167"))
        .isEqualTo(new byte[] {116, 104, 105, 115, 105, 115, 97, 102, 108, 97, 103});
  }
}
