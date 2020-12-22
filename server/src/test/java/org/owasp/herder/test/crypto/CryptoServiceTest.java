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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoFactory;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.exception.CryptographicException;

import reactor.core.publisher.Hooks;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoService unit test")
class CryptoServiceTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  CryptoService cryptoService;

  @Mock CryptoFactory cryptoFactory;

  @Test
  void hmac_GetHmacThrowsNoSuchAlgorithmException_ThrowsCryptographicException() throws Exception {
    final byte[] key = {-91, -79, 67};
    final byte[] message = {120, 56};
    when(cryptoFactory.getHmac()).thenThrow(new NoSuchAlgorithmException());
    assertThatExceptionOfType(CryptographicException.class)
        .isThrownBy(() -> cryptoService.hmac(key, message));
  }

  @Test
  void hmac_InvalidKeyException_ThrowsCryptographicException() throws Exception {
    final byte[] key = {-91};
    final byte[] message = {120, 56, 111};

    Mac mockMac = mock(Mac.class);
    when(cryptoFactory.getHmac()).thenReturn(mockMac);

    SecretKeySpec mockSecretKeySpec = mock(SecretKeySpec.class);
    when(cryptoFactory.getSecretKeySpec(key)).thenReturn(mockSecretKeySpec);

    doThrow(new InvalidKeyException()).when(mockMac).init(mockSecretKeySpec);

    assertThatExceptionOfType(CryptographicException.class)
        .isThrownBy(() -> cryptoService.hmac(key, message));
  }

  @Test
  void hmac_NullKey_ThrowsNullPointerException() {
    final byte[] message = {120, 56, 111, -98, -118, 44, -65, -127, 39, 35};

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> cryptoService.hmac(null, message));
  }

  @Test
  void hmac_NullMessage_ThrowsNullPointerException() {
    final byte[] key = {-91, -79, 67, -107, 9, 91, 62, -95, 80, 78};

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> cryptoService.hmac(key, null));
  }

  @Test
  void hmac_ValidData_ReturnsHash() throws Exception {
    final byte[] key = {-91};
    final byte[] message = {120, 56, 111};
    final byte[] expectedHash = {46};

    Mac mockMac = mock(Mac.class);
    when(cryptoFactory.getHmac()).thenReturn(mockMac);

    SecretKeySpec mockSecretKeySpec = mock(SecretKeySpec.class);
    when(cryptoFactory.getSecretKeySpec(key)).thenReturn(mockSecretKeySpec);

    when(mockMac.doFinal(message)).thenReturn(expectedHash);

    assertThat(cryptoService.hmac(key, message)).isEqualTo(expectedHash);
  }

  @BeforeEach
  private void setUp() {
    cryptoService = new CryptoService(cryptoFactory);
  }
}
