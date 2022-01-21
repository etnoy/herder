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
package org.owasp.herder.test.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.application.StartupRunner;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupRunner unit tests")
class StartupRunnerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private StartupRunner startupRunner;

  @Mock private UserService userService;

  @Mock private ModuleService moduleService;

  @Mock private XssTutorial xssTutorial;

  @Mock private SqlInjectionTutorial sqlInjectionTutorial;

  @Mock private CsrfTutorial csrfTutorial;

  @Mock private FlagTutorial flagTutorial;

  @Mock private FlagHandler flagHandler;

  @Test
  void run_MockedServices_CallsMocks() {
    final long mockUserId = 602L;
    when(userService.createPasswordUser(
            "Admin", "admin", "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G"))
        .thenReturn(Mono.just(mockUserId));

    when(userService.create(any(String.class))).thenReturn(Mono.empty());

    when(csrfTutorial.getInit()).thenReturn(Mono.empty());
    when(xssTutorial.getInit()).thenReturn(Mono.empty());
    when(flagTutorial.getInit()).thenReturn(Mono.empty());
    when(sqlInjectionTutorial.getInit()).thenReturn(Mono.empty());

    when(userService.promote(mockUserId)).thenReturn(Mono.empty());

    assertDoesNotThrow(() -> startupRunner.run(null));
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    startupRunner =
        new StartupRunner(
            userService, xssTutorial, sqlInjectionTutorial, csrfTutorial, flagTutorial);
  }
}
