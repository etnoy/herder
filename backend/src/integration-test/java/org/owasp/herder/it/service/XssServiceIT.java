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
package org.owasp.herder.it.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.module.xss.XssService;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("XssService integration tests")
class XssServiceIT extends BaseIT {

  @Autowired
  private XssService xssService;

  @Test
  @DisplayName("Can show alert for query with xss")
  void scriptAlert_ShouldShowAlert() {
    assertThat(executeQuery("<script>alert('script-xss')</script>")).isEqualTo("script-xss");
  }

  @Test
  @DisplayName("Can show now alert for query without xss")
  void noXss_ShouldNotShowAlert() {
    assertThat(executeQuery("")).isNull();
  }

  @Test
  @DisplayName("Can refresh XSS state after failure")
  void noXss_PreviousFailure_ShouldShowAlert() {
    assertThat(executeQuery("")).isNull();
    // Test for the gotcha that previous versions of xssservice didn't clear between invocations
    assertThat(executeQuery("<script>alert('previousfail')</script>")).isEqualTo("previousfail");
  }

  @Test
  @DisplayName("Can refresh XSS state after success")
  void noXss_PreviousSuccess_ShouldNotShowAlert() {
    assertThat(executeQuery("<script>alert('success')</script>")).isEqualTo("success");
    // Test for the gotcha that previous versions of xssservice didn't clear between invocations
    assertThat(executeQuery("")).isNull();
  }

  @Test
  @DisplayName("Can show alert for imgOnLoad")
  void imgOnLoad_ShouldShowAlert() {
    assertThat(executeQuery("<img src=\"#\" onload=\"alert('img-onload')\" />")).isEqualTo("img-onload");
  }

  @Test
  @DisplayName("Can show alert for ButtonOnMouseOver")
  void submitButtonOnMouseOver_ShouldShowAlert() {
    assertThat(executeQuery("<input type=\"submit\" onmouseover=\"alert('submit-mouseover')\"/>"))
      .isEqualTo("submit-mouseover");
  }

  @Test
  @DisplayName("Can show alert for ButtonOnMouseDown")
  void submitButtonOnMouseDown_ShouldShowAlert() {
    assertThat(executeQuery("<input type=\"submit\" onmousedown=\"alert('submit-mousedown')\"/>"))
      .isEqualTo("submit-mousedown");
  }

  @Test
  @DisplayName("Can show alert for imgOnLoad")
  void aOnBlur_ShouldShowAlert() {
    assertThat(executeQuery("<a onblur=alert('a-onblur') tabindex=1 id=x></a><input autofocus>")).isEqualTo("a-onblur");
  }

  @Test
  @DisplayName("Can show alert for SubmitButtonOnClick")
  void submitButtonOnClick_ShouldShowAlert() {
    assertThat(executeQuery("<input type=\"submit\" onclick=\"alert('submit-onclick')\"/>"))
      .isEqualTo("submit-onclick");
  }

  @Test
  @DisplayName("Can show alert for InputButtonOnClick")
  void inputButtonOnClick_ShouldShowAlert() {
    assertThat(executeQuery("<input type=\"button\" onclick=\"alert('input-onclick')\"/>")).isEqualTo("input-onclick");
  }

  private String executeQuery(final String query) {
    final List<String> alerts = xssService.doXss(
      "<html><head><title>Alert</title></head><body><p>Result: " + query + "</p></body></html>"
    );
    if (!alerts.isEmpty()) {
      return alerts.get(0);
    } else {
      return null;
    }
  }
}
