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
package org.owasp.herder.test.module.csrf;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.owasp.herder.test.BaseTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfTutorialController unit tests")
class CsrfTutorialControllerTest extends BaseTest {

  CsrfTutorialController csrfTutorialController;

  @Mock
  CsrfTutorial csrfTutorial;

  @Mock
  ControllerAuthentication controllerAuthentication;

  @BeforeEach
  void setup() {
    csrfTutorialController = new CsrfTutorialController(csrfTutorial, controllerAuthentication);
  }

  // TODO: cleanup
  @Test
  @DisplayName("Can get tutorial")
  void tutorial_TutorialCreated_ReturnsTutorial() {
    final String mockUserId = "id";

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));

    final CsrfTutorialResult mockCsrfTutorialResult = mock(CsrfTutorialResult.class);

    when(csrfTutorial.getTutorial(mockUserId)).thenReturn(Mono.just(mockCsrfTutorialResult));

    StepVerifier.create(csrfTutorialController.tutorial()).expectNext(mockCsrfTutorialResult).verifyComplete();
  }

  @Test
  @DisplayName("Can perform a CSRF attack")
  void attack_TutorialCreated_ReturnsTutorial() {
    final String mockUserId = "id";
    final String mockPseudonym = "abcd123";

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));

    final CsrfTutorialResult mockCsrfTutorialResult = mock(CsrfTutorialResult.class);

    when(csrfTutorial.attack(mockUserId, mockPseudonym)).thenReturn(Mono.just(mockCsrfTutorialResult));

    StepVerifier
      .create(csrfTutorialController.attack(mockPseudonym))
      .expectNext(mockCsrfTutorialResult)
      .verifyComplete();
  }
}
