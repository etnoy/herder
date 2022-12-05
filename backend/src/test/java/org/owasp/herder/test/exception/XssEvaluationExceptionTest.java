/*
 * Copyright 2018-2022 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.test.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.XssEvaluationException;
import org.owasp.herder.test.util.TestConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssEvaluationException unit tests")
class XssEvaluationExceptionTest {

  @Test
  void noArgsConstructor_NoArguments_ReturnsException() {
    assertThat(new XssEvaluationException())
      .isInstanceOf(XssEvaluationException.class);
  }

  @Test
  void messageConstructor_ValidMessage_MessageIncluded() {
    for (final String message : TestConstants.STRINGS) {
      XssEvaluationException exception = new XssEvaluationException(message);
      assertThat(exception.getMessage()).isEqualTo(message);
    }
  }

  @Test
  void messageExceptionConstructor_ValidMessageAndException_MessageIncluded() {
    for (final String message : TestConstants.STRINGS) {
      XssEvaluationException exception = new XssEvaluationException(
        message,
        new RuntimeException()
      );
      assertThat(exception.getMessage()).isEqualTo(message);
      assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
    }
  }

  @Test
  void exceptionConstructor_ValidException_MessageIncluded() {
    XssEvaluationException exception = new XssEvaluationException(
      new RuntimeException()
    );
    assertThat(exception.getCause()).isInstanceOf(RuntimeException.class);
  }
}
