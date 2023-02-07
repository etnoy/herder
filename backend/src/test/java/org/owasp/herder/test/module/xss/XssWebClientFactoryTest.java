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

import com.gargoylesoftware.htmlunit.CollectingAlertHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.module.xss.XssWebClientFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssWebClientFactory unit tests")
class XssWebClientFactoryTest {

  XssWebClientFactory xssWebClientFactory;

  @Test
  @DisplayName("Can create alert handler")
  void createAlertHandler_ReturnsAlertHandler() {
    assertThat(xssWebClientFactory.createAlertHandler()).isInstanceOf(CollectingAlertHandler.class);
  }

  @Test
  @DisplayName("Can create web client")
  void createWebClient_ReturnsWebClient() {
    assertThat(xssWebClientFactory.createWebClient()).isInstanceOf(WebClient.class);
  }

  @BeforeEach
  void setup() {
    xssWebClientFactory = new XssWebClientFactory();
  }
}
