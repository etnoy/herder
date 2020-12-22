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
package org.owasp.herder.module.xss;

import com.gargoylesoftware.htmlunit.CollectingAlertHandler;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.MockWebConnection;
import com.gargoylesoftware.htmlunit.NicelyResynchronizingAjaxController;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.owasp.herder.exception.XssEvaluationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class XssService {
  private final XssWebClientFactory xssWebClientFactory;

  public List<String> doXss(final String htmlPage) {
    MockWebConnection mockWebConnection = new MockWebConnection();

    mockWebConnection.setDefaultResponse(htmlPage);
    final WebClient webClient = xssWebClientFactory.createWebClient();
    webClient.setWebConnection(mockWebConnection);

    final CollectingAlertHandler alertHandler = xssWebClientFactory.createAlertHandler();

    webClient.setAlertHandler(alertHandler);

    HtmlPage page = null;
    webClient.setAjaxController(new NicelyResynchronizingAjaxController());
    try {
      // We make a dummy call to our mocked url
      page = webClient.getPage("http://www.example.com/");
    } catch (FailingHttpStatusCodeException | IOException e) {
      throw new XssEvaluationException(e);
    } finally {
      webClient.close();
      mockWebConnection.close();
    }

    try {
      page.initialize();
      interactWithPage(page);
    } catch (FailingHttpStatusCodeException | IOException e) {
      throw new XssEvaluationException(e);
    }

    webClient.waitForBackgroundJavaScript(1000);

    return alertHandler.getCollectedAlerts();
  }

  private void interactWithPage(final HtmlPage page) throws IOException {
    Iterator<DomElement> domElementIterator = page.getDomElementDescendants().iterator();

    while (domElementIterator.hasNext()) {
      final DomElement domElement = domElementIterator.next();
      if (domElement.isDisplayed()) {
        domElement.click();
        domElement.dblClick();
        domElement.focus();
        domElement.mouseDown();
        domElement.mouseMove();
        domElement.mouseOut();
        domElement.mouseOver();
        domElement.mouseUp();
        domElement.rightClick();
      }
    }
  }
}
