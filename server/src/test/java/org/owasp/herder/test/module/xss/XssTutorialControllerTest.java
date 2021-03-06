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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.module.xss.XssTutorialController;
import org.owasp.herder.module.xss.XssTutorialResponse;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssTutorialController unit test")
class XssTutorialControllerTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private XssTutorialController xssTutorialController;

  @Mock private ControllerAuthentication controllerAuthentication;

  @Mock private XssTutorial xssTutorial;

  @Test
  void search_Autenticated_CallsModule() {
    final long mockUserId = 527L;

    final XssTutorialResponse xssTutorialRow = mock(XssTutorialResponse.class);

    final String query = "xss";
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));

    when(xssTutorial.submitQuery(mockUserId, query)).thenReturn(Mono.just(xssTutorialRow));

    StepVerifier.create(xssTutorialController.search(query))
        .expectNext(xssTutorialRow)
        .expectComplete()
        .verify();
    verify(controllerAuthentication, times(1)).getUserId();
    verify(xssTutorial, times(1)).submitQuery(mockUserId, query);
  }

  @Test
  void search_NotAutenticated_CallsModule() {
    final String query = "xss";
    when(controllerAuthentication.getUserId())
        .thenReturn(Mono.error(new NotAuthenticatedException()));
    StepVerifier.create(xssTutorialController.search(query))
        .expectError(NotAuthenticatedException.class)
        .verify();
    verify(controllerAuthentication, times(1)).getUserId();
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    xssTutorialController = new XssTutorialController(xssTutorial, controllerAuthentication);
  }
}
