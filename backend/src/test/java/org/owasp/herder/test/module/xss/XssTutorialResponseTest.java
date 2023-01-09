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
package org.owasp.herder.test.module.xss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import lombok.NonNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.module.xss.XssTutorialResponse;
import org.owasp.herder.module.xss.XssTutorialResponse.XssTutorialResponseBuilder;
import org.owasp.herder.test.util.TestConstants;

@DisplayName("XssTutorialResponse unit tests")
class XssTutorialResponseTest {

  @Test
  void build_NullResult_ThrowsNullPointerException() {
    final XssTutorialResponseBuilder xssTutorialResponseBuilder = XssTutorialResponse
      .builder()
      .alert("xss");
    assertThatExceptionOfType(NullPointerException.class)
      .isThrownBy(() -> xssTutorialResponseBuilder.build())
      .withMessage("result is marked non-null but is null");
  }

  @Test
  void buildAlert_ValidAlert_Builds() {
    final XssTutorialResponseBuilder xssTutorialResponseBuilder = XssTutorialResponse.builder();
    for (final String alert : TestConstants.STRINGS_WITH_NULL) {
      final XssTutorialResponse xssTutorialResponse = xssTutorialResponseBuilder
        .result("result")
        .alert(alert)
        .build();
      assertThat(xssTutorialResponse.getAlert()).isEqualTo(alert);
    }
  }

  @Test
  void builderToString_ValidData_AsExpected() {
    final XssTutorialResponseBuilder testXssTutorialResponseBuilder = XssTutorialResponse
      .builder()
      .result("TestXssTutorialResponse")
      .alert("xss");
    assertThat(testXssTutorialResponseBuilder)
      .hasToString(
        "XssTutorialResponse.XssTutorialResponseBuilder(result=TestXssTutorialResponse, alert=xss)"
      );
  }

  @Test
  void buildResult_NullResult_ThrowsNullPointerException() {
    final XssTutorialResponseBuilder xssTutorialResponseBuilder = XssTutorialResponse.builder();
    assertThatExceptionOfType(NullPointerException.class)
      .isThrownBy(() -> xssTutorialResponseBuilder.result(null))
      .withMessage("result is marked non-null but is null");
  }

  @Test
  void buildResult_ValidResult_Builds() {
    final XssTutorialResponseBuilder xssTutorialResponseBuilder = XssTutorialResponse.builder();
    for (final String result : TestConstants.STRINGS) {
      final XssTutorialResponse xssTutorialResponse = xssTutorialResponseBuilder
        .result(result)
        .build();
      assertThat(xssTutorialResponse.getResult()).isEqualTo(result);
    }
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {
    EqualsVerifier
      .forClass(XssTutorialResponse.class)
      .withIgnoredAnnotations(NonNull.class)
      .verify();
  }

  @Test
  void toString_ValidData_AsExpected() {
    final XssTutorialResponse testXssTutorialResponse = XssTutorialResponse
      .builder()
      .result("result is good")
      .alert("xss warning")
      .build();
    assertThat(testXssTutorialResponse)
      .hasToString(
        "XssTutorialResponse(result=result is good, alert=xss warning)"
      );
  }
}
