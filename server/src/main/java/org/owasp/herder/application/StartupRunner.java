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
package org.owasp.herder.application;

import lombok.RequiredArgsConstructor;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.user.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@ConditionalOnProperty(
    prefix = "application.runner",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

  private final UserService userService;

  private final XssTutorial xssTutorial;

  private final SqlInjectionTutorial sqlInjectionTutorial;

  private final CsrfTutorial csrfTutorial;

  private final FlagTutorial flagTutorial;

  @Override
  public void run(ApplicationArguments args) {
    // Create a default admin account
    long userId =
        userService
            .createPasswordUser(
                "Admin", "admin", "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G")
            .block();
    userService.promote(userId).block();
    Mono.when(
            csrfTutorial.getInit(),
            flagTutorial.getInit(),
            xssTutorial.getInit(),
            sqlInjectionTutorial.getInit())
        .block();
  }
}
