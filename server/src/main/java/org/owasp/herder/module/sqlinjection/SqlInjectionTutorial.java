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

import java.util.Base64;
import lombok.EqualsAndHashCode;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.springframework.data.r2dbc.BadSqlGrammarException;
import org.springframework.data.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

  public Flux<SqlInjectionTutorialRow> submitQuery(final long userId, final String usernameQuery) {
    final String randomUserName =
        Base64.getEncoder().encodeToString(keyService.generateRandomBytes(16));
    // Generate a dynamic flag and add it as a row to the database creation script.
    // The flag is
    // different for every user to prevent copying flags
    final Mono<String> insertionQuery =
        getFlag(userId)
            // Curly braces need to be URL encoded
            .map(flag -> flag.replace("{", "%7B"))
            .map(flag -> flag.replace("}", "%7D"))
            .map(
                flag ->
                    String.format(
                        "INSERT INTO sqlinjection.users values ('%s', 'Well done, flag is %s')",
                        randomUserName, flag));

    // Create a connection URL to a H2SQL in-memory database. Each submission call
    // creates a completely new instance of this database.
    final Mono<String> connectionUrl =
        insertionQuery.map(
            query ->
                String.format(
                    "r2dbc:h2:mem:///sql-injection-tutorial-for-uid%d;"
                        // Load the initial sql file
                        + "INIT=RUNSCRIPT FROM 'classpath:module/sql-injection-tutorial.sql'"
                        + "%s%s",
                    // %5C%3B is a backslash and semicolon URL-encoded
                    userId, "%5C%3B", query));

    // Create a DatabaseClient that allows us to manually interact with the database
    final Mono<DatabaseClient> databaseClientMono =
        connectionUrl.map(sqlInjectionDatabaseClientFactory::create);

    // Create the database query. Yes, this is vulnerable to SQL injection. That's
    // the whole point.
    final String injectionQuery =
        String.format("SELECT * FROM sqlinjection.users WHERE name = '%s'", usernameQuery);

    return databaseClientMono
        // Execute database query
        .flatMapMany(
            databaseClient ->
                databaseClient
                    .execute(injectionQuery)
                    .as(SqlInjectionTutorialRow.class)
                    .fetch()
                    .all())
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
            });
  }
}
