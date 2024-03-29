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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.R2dbcBadGrammarException;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.sqlinjection.SqlInjectionDatabaseClientFactory;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.r2dbc.UncategorizedR2dbcException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlInjectionTutorial unit tests")
class SqlInjectionTutorialTest extends BaseTest {

  private String moduleLocator;

  SqlInjectionTutorial sqlInjectionTutorial;

  @Mock
  SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  @Mock
  FlagHandler flagHandler;

  @Mock
  KeyService keyService;

  final String testFlag = "mockedflag";
  final String testQuery = "username";

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Can return a meaningful error to the user when encountering BadSqlGrammarException")
  void submitQuery_BadSqlGrammarException_ReturnsErrorToUser() {
    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, moduleLocator)).thenReturn(Mono.just(testFlag));

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class);
    when(sqlInjectionDatabaseClientFactory.create(any(String.class))).thenReturn(mockDatabaseClient);

    final GenericExecuteSpec mockExecuteSpec = mock(GenericExecuteSpec.class);

    when(mockDatabaseClient.sql(any(String.class))).thenReturn(mockExecuteSpec);
    when(mockExecuteSpec.then()).thenReturn(Mono.empty());

    final FetchSpec<SqlInjectionTutorialRow> fetchSpec = mock(FetchSpec.class);

    when(mockExecuteSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);
    when(fetchSpec.all())
      .thenReturn(
        Flux.error(
          new BadSqlGrammarException(
            "Error",
            testQuery,
            new R2dbcBadGrammarException(new R2dbcBadGrammarException("Syntax error, yo", new RuntimeException()))
          )
        )
      );

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(TestConstants.TEST_USER_ID, testQuery))
      .assertNext(row -> {
        assertThat(row.getName()).isNull();
        assertThat(row.getComment()).isNull();
        assertThat(row.getError()).isEqualTo("Syntax error, yo");
      })
      .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Can return a meaningful error to the user when encountering UncategorizedR2dbcException")
  void submitQuery_UncategorizedR2dbcException_ReturnsErrorToUser() {
    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, moduleLocator)).thenReturn(Mono.just(testFlag));

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class);
    when(sqlInjectionDatabaseClientFactory.create(any(String.class))).thenReturn(mockDatabaseClient);

    final GenericExecuteSpec mockExecuteSpec = mock(GenericExecuteSpec.class);

    when(mockDatabaseClient.sql(any(String.class))).thenReturn(mockExecuteSpec);
    when(mockExecuteSpec.then()).thenReturn(Mono.empty());

    final FetchSpec<SqlInjectionTutorialRow> fetchSpec = mock(FetchSpec.class);

    when(mockExecuteSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);
    when(fetchSpec.all())
      .thenReturn(
        Flux.error(
          new UncategorizedR2dbcException(
            "Error",
            testQuery,
            new R2dbcBadGrammarException(new R2dbcBadGrammarException("Syntax error, yo", new RuntimeException()))
          )
        )
      );

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(TestConstants.TEST_USER_ID, testQuery))
      .assertNext(row -> {
        assertThat(row.getName()).isNull();
        assertThat(row.getComment()).isNull();
        assertThat(row.getError()).isEqualTo("Syntax error, yo");
      })
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    moduleLocator = sqlInjectionTutorial.getLocator();
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Can return a meaningful error to the user when encountering DataIntegrityViolationException")
  void submitQuery_DataIntegrityViolationException_ReturnsErrorToUser() {
    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, moduleLocator)).thenReturn(Mono.just(testFlag));

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class);
    when(sqlInjectionDatabaseClientFactory.create(any(String.class))).thenReturn(mockDatabaseClient);

    final GenericExecuteSpec mockExecuteSpec = mock(GenericExecuteSpec.class);

    when(mockDatabaseClient.sql(any(String.class))).thenReturn(mockExecuteSpec);

    final FetchSpec<SqlInjectionTutorialRow> fetchSpec = mock(FetchSpec.class);

    when(mockExecuteSpec.then()).thenReturn(Mono.empty());
    when(mockExecuteSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);

    when(fetchSpec.all())
      .thenReturn(
        Flux.error(
          new DataIntegrityViolationException(
            "Error",
            new DataIntegrityViolationException(
              "Error",
              new DataIntegrityViolationException("Data integrity violation, yo", new RuntimeException())
            )
          )
        )
      );

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(TestConstants.TEST_USER_ID, testQuery))
      .assertNext(row -> {
        assertThat(row.getName()).isNull();
        assertThat(row.getComment()).isNull();
        assertThat(row.getError()).isEqualTo("Data integrity violation, yo");
      })
      .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Can error if encountering an unexpected exception")
  void submitQuery_RuntimeException_ThrowsException() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, moduleLocator)).thenReturn(Mono.just(testFlag));

    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class);
    when(sqlInjectionDatabaseClientFactory.create(any(String.class))).thenReturn(mockDatabaseClient);

    final GenericExecuteSpec mockExecuteSpec = mock(GenericExecuteSpec.class);

    when(mockDatabaseClient.sql(any(String.class))).thenReturn(mockExecuteSpec);
    when(mockExecuteSpec.then()).thenReturn(Mono.empty());

    final FetchSpec<SqlInjectionTutorialRow> fetchSpec = mock(FetchSpec.class);

    when(mockExecuteSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);
    when(fetchSpec.all()).thenReturn(Flux.error(new IllegalArgumentException()));

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(TestConstants.TEST_USER_ID, testQuery))
      .expectError(IllegalArgumentException.class)
      .verify();
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("Can return a row if a valid query is given")
  void submitQuery_ValidQuery_ReturnsSqlInjectionTutorialRow() {
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, moduleLocator)).thenReturn(Mono.just(testFlag));

    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class);
    when(sqlInjectionDatabaseClientFactory.create(any(String.class))).thenReturn(mockDatabaseClient);

    final GenericExecuteSpec mockExecuteSpec = mock(GenericExecuteSpec.class);

    when(mockDatabaseClient.sql(any(String.class))).thenReturn(mockExecuteSpec);

    final FetchSpec<SqlInjectionTutorialRow> fetchSpec = mock(FetchSpec.class);
    final SqlInjectionTutorialRow mockSqlInjectionTutorialRow1 = mock(SqlInjectionTutorialRow.class);
    final SqlInjectionTutorialRow mockSqlInjectionTutorialRow2 = mock(SqlInjectionTutorialRow.class);

    when(mockExecuteSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);

    when(mockExecuteSpec.then()).thenReturn(Mono.empty());
    when(fetchSpec.all()).thenReturn(Flux.just(mockSqlInjectionTutorialRow1, mockSqlInjectionTutorialRow2));

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(TestConstants.TEST_USER_ID, testQuery))
      .expectNext(mockSqlInjectionTutorialRow1)
      .expectNext(mockSqlInjectionTutorialRow2)
      .verifyComplete();
  }
}
