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
package org.owasp.herder.module.xss;

import java.util.List;
import lombok.EqualsAndHashCode;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.xss.XssTutorialResponse.XssTutorialResponseBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@EqualsAndHashCode(callSuper = true)
public class XssTutorial extends BaseModule {
  private static final String MODULE_NAME = "xss-tutorial";

  private final XssService xssService;

  public XssTutorial(
      final XssService xssService,
      final ModuleService moduleService,
      final FlagHandler flagHandler) {
    super(MODULE_NAME, moduleService, flagHandler, null);
    this.xssService = xssService;
  }

  public Mono<XssTutorialResponse> submitQuery(final long userId, final String query) {

    final String htmlTarget =
        String.format(
            "<html><head><title>Alert</title></head><body><p>Result: %s</p></body></html>", query);

    final List<String> alerts = xssService.doXss(htmlTarget);

    final XssTutorialResponseBuilder xssTutorialResponseBuilder = XssTutorialResponse.builder();

    if (alerts.isEmpty()) {
      xssTutorialResponseBuilder.result(String.format("Sorry, found no result for %s", query));
      return Mono.just(xssTutorialResponseBuilder.build());
    } else {
      xssTutorialResponseBuilder.alert(alerts.get(0));

      return getFlag(userId)
          .map(flag -> String.format("Congratulations, flag is %s", flag))
          .map(xssTutorialResponseBuilder::result)
          .map(XssTutorialResponseBuilder::build);
    }
  }
}
