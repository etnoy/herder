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
package org.owasp.herder.module.sqlinjection;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import java.util.Base64;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.Score;
import org.owasp.herder.module.Tag;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.r2dbc.UncategorizedR2dbcException;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tutorial module for SQL injections */
@RequiredArgsConstructor
@HerderModule("SQL Injection Tutorial")
@Locator("sql-injection-tutorial")
@Score(baseScore = 100, goldBonus = 50, silverBonus = 20, bronzeBonus = 10)
@Tag(key = "topic", value = "sql-injection")
public class SqlInjectionTutorial implements BaseModule {

  private final SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  private final KeyService keyService;

  private final FlagHandler flagHandler;

  private static final BiFunction<Row, RowMetadata, SqlInjectionTutorialRow> MAPPING_FUNCTION = (row, rowMetaData) ->
    SqlInjectionTutorialRow
      .builder()
      .name(row.get("name", String.class))
      .comment(row.get("comment", String.class))
      .build();

  /**
   * Execute a given SQL injection query
   *
   * @param userId the currently logged in user id
   * @param injectionQuery the query to be executed
   * @return the result of the query
   */
  public Flux<SqlInjectionTutorialRow> submitQuery(final String userId, final String injectionQuery) {
    // Each module and user has a unique in-memory database
    final String dbName = String.format("%s-uid-%s", getName(), userId);

    final DatabaseClient databaseClient = sqlInjectionDatabaseClientFactory.create(dbName);

    // A randomly chosen "username" that contains the flag
    final String hiddenName = Base64.getEncoder().encodeToString(keyService.generateRandomBytes(16));

    // Create the SQL query used to populate the database
    final Mono<String> populationQuery =
      // Compute the flag to be hidden in the database
      flagHandler
        .getDynamicFlag(userId, getLocator())
        .map(flag ->
          String.format(
            "DROP ALL OBJECTS;" + // Clear the database
            "CREATE SCHEMA sqlinjection;" +
            // Hidden credits are always fun
            "CREATE TABLE sqlinjection.users (name VARCHAR(255) PRIMARY KEY, comment VARCHAR(255));" +
            "INSERT INTO sqlinjection.users values ('Jonathan Jogenfors', 'System Author');" +
            "INSERT INTO sqlinjection.users values ('Niklas Johansson', 'Teacher');" +
            "INSERT INTO sqlinjection.users values ('Jan-Åke Larsson', 'Professor');" +
            "INSERT INTO sqlinjection.users values ('Guilherme B. Xavier','Examiner');" +
            // This is the row that contains the flag!
            "INSERT INTO sqlinjection.users values ('%s', 'Well done, flag is %s');",
            hiddenName,
            flag
          )
        );

    // The query to be executed on the database. Note: if a vulnerability scanner has
    // issues with this line, please ignore. This is *supposed* to be vulnerable, it's a CTF after
    // all!
    final String vulnerableQuery = String.format("SELECT * FROM sqlinjection.users WHERE name = '%s'", injectionQuery);

    return populationQuery
      // Execute the population query on the in-memory database
      .flatMap(query -> databaseClient.sql(query).then())
      // Then execute the SQL injection and fetch the results
      .thenMany(databaseClient.sql(vulnerableQuery).map(MAPPING_FUNCTION).all())
      // Some errors are to be shown to the end user, while all the rest are handled as usual
      .onErrorResume(exception -> {
        // We want to forward database syntax errors and integrity violation errors to the
        // user
        if (
          (exception instanceof BadSqlGrammarException) ||
          (exception instanceof DataIntegrityViolationException) ||
          (exception instanceof UncategorizedR2dbcException)
        ) {
          return Flux.just(
            // Build a row with only the error field filled in
            SqlInjectionTutorialRow.builder().error(exception.getCause().getCause().getMessage()).build()
          );
        } else {
          // All other errors are handled in the usual way
          return Flux.error(exception);
        }
      });
  }
}
