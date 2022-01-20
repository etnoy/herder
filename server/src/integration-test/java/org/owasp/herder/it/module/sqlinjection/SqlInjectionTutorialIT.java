/*
 * Copyright 2018-2022 Jonathan Jogenfors, jonathan@jogenfors.se
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.sqlinjection.SqlInjectionDatabaseClientFactory;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("SqlInjectionTutorial integration tests")
class SqlInjectionTutorialIT {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  SqlInjectionTutorial sqlInjectionTutorial;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired SubmissionService submissionService;

  @Autowired ScoreService scoreService;

  @Autowired SqlInjectionDatabaseClientFactory sqlInjectionDatabaseClientFactory;

  @Autowired FlagHandler flagHandler;

  @Autowired KeyService keyService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @BeforeEach
  private void clear() {
    integrationTestUtils.resetState();
    sqlInjectionTutorial =
        new SqlInjectionTutorial(
            moduleService, flagHandler, sqlInjectionDatabaseClientFactory, keyService);
    sqlInjectionTutorial.getInit().block();
  }

  private String extractFlagFromRow(final SqlInjectionTutorialRow row) {
    return row.getComment().replaceAll("Well done, flag is ", "");
  }

  @Test
  void submitQuery_CorrectAttackQuery_ModifiedFlagIsWrong() {
    final Long userId = userService.create("TestUser1").block();

    final Mono<String> flagVerificationMono =
        sqlInjectionTutorial
            .submitQuery(userId, "' OR '1' = '1")
            .skip(4)
            .next()
            .map(this::extractFlagFromRow);

    // Take the flag we got from the tutorial, modify it, and expect validation to fail
    StepVerifier.create(
            flagVerificationMono
                .flatMap(
                    flag ->
                        submissionService.submit(
                            userId, sqlInjectionTutorial.getModuleName(), flag + "wrong"))
                .map(Submission::isValid))
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_CorrectAttackQuery_ReturnedFlagIsCorrect() {
    final Long userId = userService.create("TestUser1").block();

    final Mono<String> flagMono =
        sqlInjectionTutorial
            .submitQuery(userId, "' OR '1' = '1")
            .skip(4)
            .next()
            .map(this::extractFlagFromRow);

    // Submit the flag we got from the sql injection and make sure it validates
    StepVerifier.create(
            flagMono
                .flatMap(
                    flag ->
                        submissionService.submit(
                            userId, sqlInjectionTutorial.getModuleName(), flag))
                .map(Submission::isValid))
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_CorrectAttackQuery_ReturnsWholeDatabase() {
    final Long userId = userService.create("TestUser1").block();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1"))
        .expectNextCount(5)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_InjectionDeletesAll_DoesNotImpactDatabase() throws InterruptedException {
    final Long userId = userService.create("TestUser1").block();

    sqlInjectionTutorial.submitQuery(userId, "1'; DROP ALL OBJECTS; --").blockLast();
    System.out.println(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1").blockLast());

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1"))
        .expectNextCount(5)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_QueryWithNoMatches_EmptyResultSet() {
    final Long userId = userService.create("TestUser1").block();
    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "test")).expectComplete().verify();
  }

  @Test
  void submitQuery_QueryWithOneMatch_OneItemInResultSet() {
    final Long userId = userService.create("TestUser1").block();
    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "Jonathan Jogenfors"))
        .expectNextCount(1)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_RepeatedCorrectAttackQuery_ReturnsWholeDatabaseFromCache() {
    final Long userId = userService.create("TestUser1").block();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1"))
        .expectNextCount(5)
        .expectComplete()
        .verify();

    StepVerifier.create(sqlInjectionTutorial.submitQuery(userId, "' OR '1' = '1"))
        .expectNextCount(5)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_InvalidQueryWithOneApostrophe_ReturnsError() {
    final Long userId = userService.create("TestUser1").block();

    final String errorMessage =
        "org.h2.jdbc.JdbcSQLSyntaxErrorException: "
            + "Syntax error in SQL statement \"SELECT * FROM sqlinjection.users "
            + "WHERE name = ''[*]'\"; SQL statement:\n"
            + "SELECT * FROM sqlinjection.users WHERE name = ''' [42000-200]";

    StepVerifier.create(
            sqlInjectionTutorial.submitQuery(userId, "'").map(SqlInjectionTutorialRow::getError))
        .expectNext(errorMessage)
        .expectComplete()
        .verify();
  }

  @Test
  void submitQuery_InvalidQueryOneEqualsOne_NumberFormatException() {
    final Long userId = userService.create("TestUser1").block();

    final String errorMessage =
        "org.h2.jdbc.JdbcSQLDataException: Data conversion error converting \"1=1\"; SQL statement:\n"
            + "SELECT * FROM sqlinjection.users WHERE name = '' OR '1=1' [22018-200]";

    StepVerifier.create(
            sqlInjectionTutorial
                .submitQuery(userId, "' OR '1=1")
                .map(SqlInjectionTutorialRow::getError))
        .expectNext(errorMessage)
        .expectComplete()
        .verify();
  }
}
