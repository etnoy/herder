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
package org.owasp.herder.it.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.module.xss.XssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@DisplayName("XssService integration tests")
class XssServiceIT {

  @Autowired private XssService xssService;

  @Test
  void scriptAlert_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<script>alert('script-xss')</script>")).isEqualTo("script-xss");
  }

  @Test
  void noXss_ShouldNotShowAlert() throws Exception {
    assertThat(executeQuery("")).isNull();
  }

  @Test
  void noXss_PreviousFailure_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("")).isNull();
    // Test for the gotcha that previous versions of xssservice didn't clear between invocations
    assertThat(executeQuery("<script>alert('previousfail')</script>")).isEqualTo("previousfail");
  }

  @Test
  void noXss_PreviousSuccess_ShouldNotShowAlert() throws Exception {
    assertThat(executeQuery("<script>alert('success')</script>")).isEqualTo("success");
    // Test for the gotcha that previous versions of xssservice didn't clear between invocations
    assertThat(executeQuery("")).isNull();
  }

  @Test
  void imgOnLoad_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<img src=\"#\" onload=\"alert('img-onload')\" />"))
        .isEqualTo("img-onload");
  }

  @Test
  void submitButtonOnMouseOver_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<input type=\"submit\" onmouseover=\"alert('submit-mouseover')\"/>"))
        .isEqualTo("submit-mouseover");
  }

  @Test
  void submitButtonOnMouseDown_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<input type=\"submit\" onmousedown=\"alert('submit-mousedown')\"/>"))
        .isEqualTo("submit-mousedown");
  }

  @Test
  void aOnBlur_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<a onblur=alert('a-onblur') tabindex=1 id=x></a><input autofocus>"))
        .isEqualTo("a-onblur");
  }

  @Test
  void submitButtonOnClick_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<input type=\"submit\" onclick=\"alert('submit-onclick')\"/>"))
        .isEqualTo("submit-onclick");
  }

  @Test
  void inputButtonOnClick_ShouldShowAlert() throws Exception {
    assertThat(executeQuery("<input type=\"button\" onclick=\"alert('input-onclick')\"/>"))
        .isEqualTo("input-onclick");
  }

  private String executeQuery(final String query) throws IOException {
    final List<String> alerts =
        xssService.doXss(
            "<html><head><title>Alert</title></head><body><p>Result: "
                + query
                + "</p></body></html>");
    if (!alerts.isEmpty()) {
      return alerts.get(0);
    } else {
      return null;
    }
  }
}
