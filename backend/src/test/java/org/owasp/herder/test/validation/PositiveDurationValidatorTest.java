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
package org.owasp.herder.test.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.validation.PositiveDurationValidator;

@ExtendWith(MockitoExtension.class)
@DisplayName("PositiveDurationValidator unit tests")
class PositiveDurationValidatorTest extends BaseTest {

  PositiveDurationValidator positiveDurationValidator;

  @BeforeEach
  void setup() {
    positiveDurationValidator = new PositiveDurationValidator();
  }

  @Test
  @DisplayName("A negative duration should fail validation")
  void isValid_NegativeDuration_ShouldFailValidation() {
    assertThat(positiveDurationValidator.isValid(Duration.ofDays(-1), null)).isFalse();
  }

  @Test
  @DisplayName("A zero duration should pass validation")
  void isValid_ZeroDuration_ShouldPassValidation() {
    assertThat(positiveDurationValidator.isValid(Duration.ZERO, null)).isTrue();
  }

  @Test
  @DisplayName("A positive duration should pass validation")
  void isValid_PositiveDuration_ShouldPassValidation() {
    assertThat(positiveDurationValidator.isValid(Duration.ofDays(1), null)).isTrue();
  }
}
