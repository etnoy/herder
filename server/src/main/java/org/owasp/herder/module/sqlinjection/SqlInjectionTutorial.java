/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.module.sqlinjection;

import io.r2dbc.h2.H2ConnectionConfiguration;
import io.r2dbc.h2.H2ConnectionFactory;
import java.util.Base64;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Spring R2dbc 1.2 lacks features present in version 1.1, we have to use deprecated functions until
// this is fixed
@SuppressWarnings("deprecation")
@Component
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class SqlInjectionTutorial extends BaseModule {

  private static final String MODULE_NAME = "sql-injection-tutorial";

  private static final int DB_CLOSE_DELAY = 600;

  private final SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  private final KeyService keyService;

  public SqlInjectionTutorial(
      final ModuleService moduleService,
      final FlagHandler flagHandler,
      final SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory,
      final KeyService keyService) {
    super(MODULE_NAME, moduleService, flagHandler, null);
    this.sqlInjectionDatabaseClientFactory = sqlInjectionDatabaseClientFactory;
    this.keyService = keyService;
  }

  public Mono<Void> populate(DatabaseClient databaseClient, final long userId) {
    final String hiddenName =
        Base64.getEncoder().encodeToString(keyService.generateRandomBytes(16));

    final String populationQuery =
        "DROP ALL OBJECTS;"
            + "CREATE SCHEMA sqlinjection;"
            + "CREATE TABLE sqlinjection.users (name VARCHAR(255) PRIMARY KEY, comment VARCHAR(255));"
            + "INSERT INTO sqlinjection.users values ('Jonathan Jogenfors', 'System Author');"
            + "INSERT INTO sqlinjection.users values ('Niklas Johansson', 'Teacher');"
            + "INSERT INTO sqlinjection.users values ('Jan-Åke Larsson', 'Professor');"
            + "INSERT INTO sqlinjection.users values ('Guilherme B. Xavier','Examiner');"
            + "INSERT INTO sqlinjection.users values ('OR 1=1', 'You are close! Surround the query with single quotes so that your code is interpreted');";

    final String flagQuery = "INSERT INTO sqlinjection.users values ($1, $2);";

    log.trace("Populating SQL Injection Tutorial database");

    return databaseClient
        .execute(populationQuery)
        .then()
        .then(getFlag(userId))
        .flatMap(
            flag ->
                databaseClient
                    .execute(flagQuery)
                    .bind("$1", hiddenName)
                    .bind("$2", "Well done, flag is " + flag)
                    .then())
        .then();
  }

  public DatabaseClient getDatabaseClient(final long userId) {
    return getDatabaseClient(userId, "");
  }

  public DatabaseClient getDatabaseClient(final long userId, final String dbOptions) {
    final String dbName = String.format("%s-uid-%d", MODULE_NAME, userId);

    final H2ConnectionConfiguration h2DatabaseConfig =
        H2ConnectionConfiguration.builder().inMemory(dbName).option(dbOptions).build();

    final H2ConnectionFactory connectionFactory = new H2ConnectionFactory(h2DatabaseConfig);

    return DatabaseClient.create(connectionFactory);
  }

  public Flux<SqlInjectionTutorialRow> submitQuery(final long userId, final String usernameQuery) {

    final String dbCloseDelayOption = String.format("DB_CLOSE_DELAY=%d;", DB_CLOSE_DELAY);

    // Create a DatabaseClient that allows us to manually interact with the database
    final DatabaseClient databaseClient = getDatabaseClient(userId, dbCloseDelayOption);

    final DatabaseClient cachedDatabaseClient = getDatabaseClient(userId, "IFEXISTS=TRUE;");

    // Create the database query. Yes, this is vulnerable to SQL injection. That's
    // the whole point.
    final String injectionQuery =
        String.format("SELECT * FROM sqlinjection.users WHERE name = '%s'", usernameQuery);

    final Flux<SqlInjectionTutorialRow> cachedResult =
        cachedDatabaseClient
            .execute(injectionQuery)
            .as(SqlInjectionTutorialRow.class)
            .fetch()
            .all();

    final Flux<SqlInjectionTutorialRow> freshResult =
        populate(databaseClient, userId)
            // Execute database query
            .thenMany(
                databaseClient
                    .execute(injectionQuery)
                    .as(SqlInjectionTutorialRow.class)
                    .fetch()
                    .all());

    return cachedResult
        .onErrorResume(
            exception -> {
              // TODO: must have finer-grained exception filter here
              if (exception instanceof DataAccessResourceFailureException) {
                // TODO: remove this debug info
                log.trace("Cache miss");
                // Cache miss

                return freshResult;

              } else {
                // All other errors are handled in the usual way
                return Flux.error(exception);
              }
            })
        // Handle errors
        .onErrorResume(
            exception -> {
              // We want to forward database syntax errors to the user
              if (exception instanceof BadSqlGrammarException) {
                return Flux.just(
                    SqlInjectionTutorialRow.builder()
                        .error(exception.getCause().toString())
                        .build());

              } else {
                // All other errors are handled in the usual way
                return Flux.error(exception);
              }
            })
        .doAfterTerminate(
            () -> {
              log.info("Re-populate");
              populate(databaseClient, userId).subscribe();
              log.info("Re-populated done");
            });
  }
}
