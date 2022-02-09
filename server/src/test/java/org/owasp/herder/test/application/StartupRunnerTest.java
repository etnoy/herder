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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.application.StartupRunner;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.user.UserService;
import org.springframework.r2dbc.core.DatabaseClient;

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

  @Mock private DatabaseClient databaseClient;

  @Test
  void run_NoArguments_Success() {
    final long mockUserId = 602L;
    when(userService.createPasswordUser(
            "Administrator",
            "admin",
            "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G"))
        .thenReturn(Mono.just(mockUserId));

    when(userService.create(any(String.class))).thenReturn(Mono.empty());

    when(userService.promote(mockUserId)).thenReturn(Mono.empty());

    when(userService.existsByLoginName(any(String.class))).thenReturn(Mono.just(false));
    when(userService.existsByDisplayName(any(String.class))).thenReturn(Mono.just(false));

    databaseClient = mock(DatabaseClient.class, Mockito.RETURNS_DEEP_STUBS);

    startupRunner = new StartupRunner(userService, databaseClient);

    Map<String, Object> versionMap = new HashMap<>();
    versionMap.put("version", "8.0.28");

    when(databaseClient.sql("select @@version").fetch().first()).thenReturn(Mono.just(versionMap));

    Map<String, Object> commentMap = new HashMap<>();
    commentMap.put("comment", "MySQL");

    when(databaseClient.sql("select @@version_comment").fetch().first())
        .thenReturn(Mono.just(commentMap));

    assertDoesNotThrow(() -> startupRunner.run(null));
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test

    startupRunner = new StartupRunner(userService, databaseClient);
  }
}
