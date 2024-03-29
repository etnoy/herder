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
package org.owasp.herder.test.module.sql;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialController;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.test.BaseTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlInjectionTutorialController unit tests")
class SqlInjectionTutorialControllerTest extends BaseTest {

  private SqlInjectionTutorialController sqlInjectionTutorialController;

  @Mock
  private ControllerAuthentication controllerAuthentication;

  @Mock
  private SqlInjectionTutorial sqlInjectionTutorial;

  @Test
  @DisplayName("Can get rows when searching")
  void search_Autenticated_CallsModule() {
    final String mockUserId = "id";

    final SqlInjectionTutorialRow sqlInjectionTutorialRow1 = mock(SqlInjectionTutorialRow.class);
    final SqlInjectionTutorialRow sqlInjectionTutorialRow2 = mock(SqlInjectionTutorialRow.class);
    final SqlInjectionTutorialRow sqlInjectionTutorialRow3 = mock(SqlInjectionTutorialRow.class);
    final String query = "sql";

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(mockUserId));
    when(sqlInjectionTutorial.submitQuery(mockUserId, query))
      .thenReturn(Flux.just(sqlInjectionTutorialRow1, sqlInjectionTutorialRow2, sqlInjectionTutorialRow3));

    StepVerifier
      .create(sqlInjectionTutorialController.search(query))
      .expectNext(sqlInjectionTutorialRow1)
      .expectNext(sqlInjectionTutorialRow2)
      .expectNext(sqlInjectionTutorialRow3)
      .verifyComplete();

    verify(controllerAuthentication, times(1)).getUserId();
    verify(sqlInjectionTutorial, times(1)).submitQuery(mockUserId, query);
  }

  @Test
  @DisplayName("Can error when searching while not authenticated")
  void search_NotAutenticated_Errors() {
    final String query = "sql";
    when(controllerAuthentication.getUserId()).thenReturn(Mono.error(new NotAuthenticatedException()));
    StepVerifier
      .create(sqlInjectionTutorialController.search(query))
      .expectError(NotAuthenticatedException.class)
      .verify();
    verify(controllerAuthentication, times(1)).getUserId();
  }

  @BeforeEach
  void setup() {
    sqlInjectionTutorialController = new SqlInjectionTutorialController(sqlInjectionTutorial, controllerAuthentication);
  }
}
