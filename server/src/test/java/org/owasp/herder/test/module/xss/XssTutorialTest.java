/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
import org.owasp.herder.module.xss.XssService;
import org.owasp.herder.module.xss.XssTutorial;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssTutorial unit test")
class XssTutorialTest {
  private static final String MODULE_NAME = "xss-tutorial";

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  XssTutorial xssTutorial;

  @Mock ModuleService moduleService;

  @Mock XssService xssService;

  @Mock FlagHandler flagHandler;

  final Module mockModule = mock(Module.class);

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    xssTutorial = new XssTutorial(xssService, moduleService, flagHandler);
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {

    class XssTutorialChild extends XssTutorial {
      public XssTutorialChild(
          XssService xssService, ModuleService moduleService, FlagHandler flagHandler) {
        super(xssService, moduleService, flagHandler);
      }

      @Override
      public boolean canEqual(Object o) {
        return false;
      }
    }

    EqualsVerifier.forClass(XssTutorial.class)
        .withRedefinedSuperclass()
        .withRedefinedSubclass(XssTutorialChild.class)
        .withIgnoredAnnotations(NonNull.class)
        .verify();
  }

  @Test
  void submitQuery_MakesAlert_ReturnsFlag() {
    final long mockUserId = 606L;
    final String mockFlag = "mockedflag";
    final String query = "username";

    when(mockModule.getName()).thenReturn(MODULE_NAME);
    when(flagHandler.getDynamicFlag(mockUserId, MODULE_NAME)).thenReturn(Mono.just(mockFlag));
    when(mockModule.isFlagStatic()).thenReturn(false);

    final String mockTarget =
        "<html><head><title>Alert</title></head><body><p>Result: username</p></body></html>";

    final List<String> mockAlertList = Arrays.asList(new String[] {"xss", "alert"});

    when(xssService.doXss(mockTarget)).thenReturn(mockAlertList);

    StepVerifier.create(xssTutorial.submitQuery(mockUserId, query))
        .assertNext(
            response -> {
              assertThat(response.getResult()).contains(mockFlag);
              assertThat(response.getAlert()).isEqualTo(mockAlertList.get(0));
            })
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_NoAlert_ReturnsQuery() {
    final long mockUserId = 606L;
    final String query = "username";

    final String mockTarget =
        "<html><head><title>Alert</title></head><body><p>Result: username</p></body></html>";

    final List<String> mockAlertList = new ArrayList<String>();

    when(xssService.doXss(mockTarget)).thenReturn(mockAlertList);

    StepVerifier.create(xssTutorial.submitQuery(mockUserId, query))
        .assertNext(
            response -> {
              assertThat(response.getResult()).contains("Sorry");
              assertThat(response.getResult()).doesNotContain("Congratulations");
              assertThat(response.getAlert()).isNull();
            })
        .expectComplete()
        .verify();
  }
}
