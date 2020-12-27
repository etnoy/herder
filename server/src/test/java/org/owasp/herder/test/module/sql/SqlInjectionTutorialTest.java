/*
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.owasp.herder.test.module.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.sqlinjection.SqlInjectionDatabaseClientFactory;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.BadSqlGrammarException;
import io.r2dbc.spi.R2dbcBadGrammarException;
import lombok.NonNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("deprecation")
@ExtendWith(MockitoExtension.class)
@DisplayName("SqlInjectionTutorial unit test")
class SqlInjectionTutorialTest {

  private static final String MODULE_NAME = "sql-injection-tutorial";

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  SqlInjectionTutorial sqlInjectionTutorial;

  @Mock
  SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  @Mock
  ModuleService moduleService;

  @Mock
  FlagHandler flagHandler;

  @Mock
  KeyService keyService;

  private DatabaseClient getClient(final String args, final Flux<SqlInjectionTutorialRow> rows) {
    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);
    when(mockDatabaseClient.execute(any(String.class)).as(SqlInjectionTutorialRow.class).fetch()
        .all()).thenReturn(rows);
    return mockDatabaseClient;
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {

    class SqlInjectionTutorialChild extends SqlInjectionTutorial {

      public SqlInjectionTutorialChild(ModuleService moduleService, FlagHandler flagHandler,
          SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory,
          KeyService keyService) {
        super(moduleService, flagHandler, sqlInjectionDatabaseClientFactory, keyService);
      }

      @Override
      public boolean canEqual(Object o) {
        return false;
      }
    }

    EqualsVerifier.forClass(SqlInjectionTutorial.class).withRedefinedSuperclass()
        .withRedefinedSubclass(SqlInjectionTutorialChild.class)
        .withIgnoredAnnotations(NonNull.class).verify();
  }

  @Test
  void submitQuery_BadSqlGrammarException_ReturnsErrorToUser() {
    final long mockUserId = 318L;
    final Module mockModule = mock(Module.class);
    final String mockFlag = "mockedflag";
    final String query = "username";

    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    sqlInjectionTutorial = new SqlInjectionTutorial(moduleService, flagHandler,
        sqlInjectionDatabaseClientFactory, keyService);

    final byte[] randomBytes = {120, 56, 111};
    when(keyService.generateRandomBytes(16)).thenReturn(randomBytes);

    when(flagHandler.getDynamicFlag(mockUserId, MODULE_NAME)).thenReturn(Mono.just(mockFlag));

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);
    when(sqlInjectionDatabaseClientFactory.create(any())).thenReturn(mockDatabaseClient);

    when(mockDatabaseClient.execute(any(String.class)).as(SqlInjectionTutorialRow.class).fetch()
        .all())
            .thenReturn(Flux.error(new BadSqlGrammarException("Error", query,
                new R2dbcBadGrammarException("Syntax error, yo"))));

    StepVerifier.create(sqlInjectionTutorial.submitQuery(mockUserId, query)).assertNext(row -> {
      assertThat(row.getName()).isNull();
      assertThat(row.getComment()).isNull();
      assertThat(row.getError())
          .isEqualTo("io.r2dbc.spi.R2dbcBadGrammarException: Syntax error, yo");
    }).verifyComplete();
  }

  @Test
  void submitQuery_ValidQuery_ReturnsSqlInjectionTutorialRow() {
    final long mockUserId = 606L;
    final Module mockModule = mock(Module.class);
    final String mockFlag = "mockedflag";
    final String query = "username";

    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    when(flagHandler.getDynamicFlag(mockUserId, MODULE_NAME)).thenReturn(Mono.just(mockFlag));

    sqlInjectionTutorial = new SqlInjectionTutorial(moduleService, flagHandler,
        sqlInjectionDatabaseClientFactory, keyService);

    sqlInjectionTutorial.getInit().block();

    final byte[] randomBytes = {120, 56, 111, 95, 6, 3};
    when(keyService.generateRandomBytes(16)).thenReturn(randomBytes);

    final SqlInjectionTutorialRow mockSqlInjectionTutorialRow1 =
        mock(SqlInjectionTutorialRow.class);
    final SqlInjectionTutorialRow mockSqlInjectionTutorialRow2 =
        mock(SqlInjectionTutorialRow.class);

    when(sqlInjectionDatabaseClientFactory.create(any(String.class)))
        .thenAnswer(args -> getClient(args.getArgument(0, String.class),
            Flux.just(mockSqlInjectionTutorialRow1, mockSqlInjectionTutorialRow2)));

    StepVerifier.create(sqlInjectionTutorial.submitQuery(mockUserId, query))
        .expectNext(mockSqlInjectionTutorialRow1).expectNext(mockSqlInjectionTutorialRow2)
        .expectComplete().verify();
  }

  @Test
  void submitQuery_OtherException_ThrowsException() {
    final long mockUserId = 810L;
    final Module mockModule = mock(Module.class);
    final String query = "username";

    final byte[] randomBytes = {120, 56, 111};
    when(keyService.generateRandomBytes(16)).thenReturn(randomBytes);

    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    sqlInjectionTutorial = new SqlInjectionTutorial(moduleService, flagHandler,
        sqlInjectionDatabaseClientFactory, keyService);

    final DatabaseClient mockDatabaseClient = mock(DatabaseClient.class, RETURNS_DEEP_STUBS);

    when(mockDatabaseClient.execute(any(String.class)).as(SqlInjectionTutorialRow.class).fetch()
        .all()).thenReturn(Flux.error(new RuntimeException()));

    StepVerifier.create(sqlInjectionTutorial.submitQuery(mockUserId, query))
        .expectError(RuntimeException.class);
  }
}
