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

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvalidFlagRateLimiter unit tests")
class InvalidFlagRateLimiterTest extends BaseTest {

  final InvalidFlagRateLimiter invalidFlagRateLimiter = new InvalidFlagRateLimiter();

  @Test
  @DisplayName("Can resolve bucket")
  void transformBuilder_ReturnsLocalValidatorFactoryBean() {
    assertThat(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).isInstanceOf(Bucket.class);
    assertThat(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).isNotNull();
  }
}
