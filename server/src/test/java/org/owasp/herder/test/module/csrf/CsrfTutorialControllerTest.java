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
package org.owasp.herder.test.module.csrf;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.csrf.CsrfTutorialController;
import org.owasp.herder.module.csrf.CsrfTutorialResult;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfTutorialController unit test")
class CsrfTutorialControllerTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  CsrfTutorialController csrfTutorialController;

  @Mock CsrfTutorial csrfTutorial;

  @Mock ControllerAuthentication controllerAuthentication;

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    csrfTutorialController = new CsrfTutorialController(csrfTutorial, controllerAuthentication);
  }

  @Test
  void tutorial_TutorialCreated_ReturnsTutorial() {
    final Long mockUserId = 85L;

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));

    final CsrfTutorialResult mockCsrfTutorialResult = mock(CsrfTutorialResult.class);

    when(csrfTutorial.getTutorial(mockUserId)).thenReturn(Mono.just(mockCsrfTutorialResult));

    StepVerifier.create(csrfTutorialController.tutorial())
        .expectNext(mockCsrfTutorialResult)
        .expectComplete()
        .verify();
  }

  @Test
  void activate_TutorialCreated_ReturnsTutorial() {
    final Long mockUserId = 85L;
    final String mockPseudonym = "abcd123";

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));

    final CsrfTutorialResult mockCsrfTutorialResult = mock(CsrfTutorialResult.class);

    when(csrfTutorial.attack(mockUserId, mockPseudonym))
        .thenReturn(Mono.just(mockCsrfTutorialResult));

    StepVerifier.create(csrfTutorialController.attack(mockPseudonym))
        .expectNext(mockCsrfTutorialResult)
        .expectComplete()
        .verify();
  }
}
