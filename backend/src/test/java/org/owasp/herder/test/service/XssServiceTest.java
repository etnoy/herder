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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gargoylesoftware.htmlunit.CollectingAlertHandler;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.XssEvaluationException;
import org.owasp.herder.module.xss.XssService;
import org.owasp.herder.module.xss.XssWebClientFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssService unit tests")
class XssServiceTest {

  XssService xssService;

  @Mock
  XssWebClientFactory xssWebClientFactory;

  @Test
  void doXss_AlertHandlerFindsAlerts_ReturnsCollectedAlerts()
    throws FailingHttpStatusCodeException, MalformedURLException, IOException {
    final String htmlPage = "<html></html>";
    final HtmlPage mockPage = mock(HtmlPage.class);
    final DomElement mockElement1 = mock(DomElement.class);
    final DomElement mockElement2 = mock(DomElement.class);
    final DomElement mockElement3 = mock(DomElement.class);

    final List<String> alerts = Arrays.asList(new String[] { "XSS", "Hello World" });
    final List<DomElement> mockDomElements = Arrays.asList(
      new DomElement[] { mockElement1, mockElement2, mockElement3 }
    );

    when(mockElement1.isDisplayed()).thenReturn(true);
    when(mockElement2.isDisplayed()).thenReturn(false);
    when(mockElement3.isDisplayed()).thenReturn(true);

    final WebClient mockWebClient = mock(WebClient.class);
    final CollectingAlertHandler mockAlertHandler = mock(CollectingAlertHandler.class);
    when(xssWebClientFactory.createWebClient()).thenReturn(mockWebClient);
    when(xssWebClientFactory.createAlertHandler()).thenReturn(mockAlertHandler);

    when(mockWebClient.getPage(any(String.class))).thenReturn(mockPage);
    when(mockPage.getDomElementDescendants()).thenReturn(mockDomElements);
    when(mockAlertHandler.getCollectedAlerts()).thenReturn(alerts);
    assertThat(xssService.doXss(htmlPage)).isEqualTo(alerts);
    verify(mockWebClient, times(1)).getPage(any(String.class));
    verify(mockAlertHandler, times(1)).getCollectedAlerts();
  }

  @Test
  void doXss_GetPageThrowsIOException_ThrowsXssEvaluationException()
    throws FailingHttpStatusCodeException, MalformedURLException, IOException {
    final String htmlPage = "<html></html>";
    final WebClient mockWebClient = mock(WebClient.class);
    final CollectingAlertHandler mockAlertHandler = mock(CollectingAlertHandler.class);
    when(xssWebClientFactory.createWebClient()).thenReturn(mockWebClient);
    when(xssWebClientFactory.createAlertHandler()).thenReturn(mockAlertHandler);
    when(mockWebClient.getPage(any(String.class))).thenThrow(new IOException());
    assertThrows(XssEvaluationException.class, () -> xssService.doXss(htmlPage));
    verify(mockWebClient, times(1)).getPage(any(String.class));
  }

  @Test
  void doXss_PageInitializeThrowsIOException_ThrowsXssEvaluationException() throws Exception {
    final String htmlPage = "<html></html>";
    final HtmlPage mockPage = mock(HtmlPage.class);
    final WebClient mockWebClient = mock(WebClient.class);
    final CollectingAlertHandler mockAlertHandler = mock(CollectingAlertHandler.class);
    when(xssWebClientFactory.createWebClient()).thenReturn(mockWebClient);
    when(xssWebClientFactory.createAlertHandler()).thenReturn(mockAlertHandler);

    when(mockWebClient.getPage(any(String.class))).thenReturn(mockPage);
    doThrow(new IOException()).when(mockPage).initialize();
    assertThrows(XssEvaluationException.class, () -> xssService.doXss(htmlPage));
    verify(mockWebClient, times(1)).getPage(any(String.class));
    verify(mockPage, times(1)).initialize();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    xssService = new XssService(xssWebClientFactory);
  }
}
