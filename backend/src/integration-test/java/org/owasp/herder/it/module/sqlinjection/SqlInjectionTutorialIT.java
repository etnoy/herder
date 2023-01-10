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
package org.owasp.herder.it.module.sqlinjection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.sqlinjection.SqlInjectionDatabaseClientFactory;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("SqlInjectionTutorial integration tests")
class SqlInjectionTutorialIT extends BaseIT {

  SqlInjectionTutorial sqlInjectionTutorial;

  @Autowired
  UserService userService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  KeyService keyService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  ModuleInitializer moduleInitializer;

  String moduleId;

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    moduleInitializer = new ModuleInitializer(null, moduleService);

    sqlInjectionTutorial = new SqlInjectionTutorial(sqlInjectionDatabaseClientFactory, keyService, flagHandler);

    moduleId = moduleInitializer.initializeModule(sqlInjectionTutorial).block();
  }

  private String extractFlagFromRow(final SqlInjectionTutorialRow row) {
    return row.getComment().replaceAll("Well done, flag is ", "");
  }

  @Test
  void submitQuery_CorrectAttackQuery_ModifiedFlagIsWrong() {
    final String userId = userService.create("TestUser1").block();

    final Mono<String> flagVerificationMono = sqlInjectionTutorial
      .submitQuery(userId, "' OR '1' = '1")
      .skip(4)
      .next()
      .map(this::extractFlagFromRow);

    // Take the flag we got from the tutorial, modify it, and expect validation to fail
    StepVerifier
      .create(
        flagVerificationMono
          .flatMap(flag -> submissionService.submitFlag(userId, moduleId, flag + "wrong"))
          .map(Submission::isValid)
      )
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  void submitQuery_CorrectAttackQuery_ReturnedFlagIsCorrect() {
    final String userId = userService.create("TestUser1").block();

    final Mono<String> flagMono = sqlInjectionTutorial
      .submitQuery(userId, "' OR '1' = '1")
      .skip(4)
      .next()
      .map(this::extractFlagFromRow);

    // Submit the flag we got from the sql injection and make sure it validates
    StepVerifier
      .create(flagMono.flatMap(flag -> submissionService.submitFlag(userId, moduleId, flag)).map(Submission::isValid))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  void submitQuery_CorrectAttackQuery_ReturnsWholeDatabase() {
    final String userId = userService.create("TestUser1").block();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1")).expectNextCount(5).verifyComplete();
  }

  @Test
  void submitQuery_InjectionDeletesAll_DoesNotImpactDatabase() throws InterruptedException {
    final String userId = userService.create("TestUser1").block();

    sqlInjectionTutorial.submitQuery(userId, "1'; DROP ALL OBJECTS; --").blockLast();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1")).expectNextCount(5).verifyComplete();
  }

  @Test
  void submitQuery_QueryWithNoMatches_EmptyResultSet() {
    final String userId = userService.create("TestUser1").block();
    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "test")).verifyComplete();
  }

  @Test
  void submitQuery_QueryWithOneMatch_OneItemInResultSet() {
    final String userId = userService.create("TestUser1").block();
    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(userId, "Jonathan Jogenfors"))
      .expectNextCount(1)
      .verifyComplete();
  }

  @Test
  void submitQuery_RepeatedCorrectAttackQuery_ReturnsWholeDatabaseFromCache() {
    final String userId = userService.create("TestUser1").block();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1")).expectNextCount(5).verifyComplete();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1")).expectNextCount(5).verifyComplete();
  }

  @Test
  void submitQuery_InvalidQueryWithOneApostrophe_ReturnsError() {
    final String userId = userService.create("TestUser1").block();

    final String errorMessage =
      "Syntax error in SQL statement \"SELECT * FROM sqlinjection.users " +
      "WHERE name = [*]'''\"; SQL statement:\n" +
      "SELECT * FROM sqlinjection.users WHERE name = '''";

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(userId, "'").map(SqlInjectionTutorialRow::getError))
      .expectNextMatches(message -> message.startsWith(errorMessage))
      .verifyComplete();
  }

  @Test
  void submitQuery_InvalidQueryOneEqualsOne_NumberFormatException() {
    final String userId = userService.create("TestUser1").block();

    final String errorMessage =
      "Data conversion error converting \"CHARACTER VARYING to BOOLEAN\"; SQL statement:\n" +
      "SELECT * FROM sqlinjection.users WHERE name = '' OR '1=1'";

    StepVerifier
      .create(sqlInjectionTutorial.submitQuery(userId, "' OR '1=1").map(SqlInjectionTutorialRow::getError))
      .expectNextMatches(message -> message.startsWith(errorMessage))
      .verifyComplete();
  }
}
