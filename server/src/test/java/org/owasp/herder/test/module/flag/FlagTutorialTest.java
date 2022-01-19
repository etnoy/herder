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
package org.owasp.herder.test.module.flag;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import lombok.NonNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.flag.FlagTutorial;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagTutorial unit tests")
class FlagTutorialTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private static final String MODULE_NAME = "flag-tutorial";

  FlagTutorial flagTutorial;

  @Mock ModuleService moduleService;

  @Mock FlagHandler flagHandler;

  final Module mockModule = mock(Module.class);

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    reset(mockModule);
    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    flagTutorial = new FlagTutorial(moduleService, flagHandler);
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {
    class FlagTutorialChild extends FlagTutorial {
      public FlagTutorialChild(ModuleService moduleService, FlagHandler flagHandler) {
        super(moduleService, flagHandler);
      }

      @Override
      public boolean canEqual(Object o) {
        return false;
      }
    }

    EqualsVerifier.forClass(FlagTutorial.class)
        .withRedefinedSubclass(FlagTutorialChild.class)
        .withIgnoredAnnotations(NonNull.class)
        .verify();
  }
}
