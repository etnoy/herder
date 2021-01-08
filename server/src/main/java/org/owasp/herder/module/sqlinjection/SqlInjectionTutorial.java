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
package org.owasp.herder.module.sqlinjection;

import java.util.Base64;
import lombok.EqualsAndHashCode;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.r2dbc.BadSqlGrammarException;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Spring R2dbc 1.2 lacks features present in version 1.1, we have to use deprecated functions until
// this is fixed
/** Tutorial module for SQL injections */
@SuppressWarnings("deprecation")
@Component
@EqualsAndHashCode(callSuper = true)
public class SqlInjectionTutorial extends BaseModule {

  private static final String MODULE_NAME = "sql-injection-tutorial";

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

  /**
   * Execute a given SQL injection query
   *
   * @param userId the currently logged in user id
   * @param injectionQuery the query to be executed
   * @return the result of the query
   */
  public Flux<SqlInjectionTutorialRow> submitQuery(final long userId, final String injectionQuery) {

    // Each module and user has a unique in-memory database
    final String dbName = String.format("%s-uid-%d", MODULE_NAME, userId);

    final DatabaseClient databaseClient = sqlInjectionDatabaseClientFactory.create(dbName);

    // A randomly chosen "username" that contains the flag
    final String hiddenName =
        Base64.getEncoder().encodeToString(keyService.generateRandomBytes(16));

    // Create the SQL query used to populate the database
    final Mono<String> populationQuery =
        // Compute the flag to be hidden in the database
        getFlag(userId)
            .map(
                flag ->
                    String.format(
                        "DROP ALL OBJECTS;" // Clear the database
                            + "CREATE SCHEMA sqlinjection;"
                            // Hidden credits are always fun
                            + "CREATE TABLE sqlinjection.users (name VARCHAR(255) PRIMARY KEY, comment VARCHAR(255));"
                            + "INSERT INTO sqlinjection.users values ('Jonathan Jogenfors', 'System Author');"
                            + "INSERT INTO sqlinjection.users values ('Niklas Johansson', 'Teacher');"
                            + "INSERT INTO sqlinjection.users values ('Jan-Åke Larsson', 'Professor');"
                            + "INSERT INTO sqlinjection.users values ('Guilherme B. Xavier','Examiner');"
                            // This is the row that contains the flag!
                            + "INSERT INTO sqlinjection.users values ('%s', 'Well done, flag is %s');",
                        hiddenName, flag));

    // The query to be executed on the database. Note: if a vulnerability scanner has
    // issues with this line, please ignore. This is *supposed* to be vulnerable, it's a CTF after
    // all!
    final String vulnerableQuery =
        String.format("SELECT * FROM sqlinjection.users WHERE name = '%s'", injectionQuery);

    return
    // Take the SQL query for initial population
    populationQuery
        // Execute it on the in-memory database
        .flatMap(query -> databaseClient.execute(query).then())
        // Then execute the SQL injection and fetch the results
        .thenMany(
            databaseClient.execute(vulnerableQuery).as(SqlInjectionTutorialRow.class).fetch().all())
        // Some errors are to be shown to the end user, while all the rest are handled as usual
        .onErrorResume(
            exception -> {
              // We want to forward database syntax errors and integrity violation errors to the
              // user
              if ((exception instanceof BadSqlGrammarException)
                  || (exception instanceof DataIntegrityViolationException)) {
                return Flux.just(
                    // Build a row with only the error field filled in
                    SqlInjectionTutorialRow.builder()
                        .error(exception.getCause().getCause().toString())
                        .build());
              } else {
                // All other errors are handled in the usual way
                return Flux.error(exception);
              }
            });
  }
}
