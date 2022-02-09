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
package org.owasp.herder.application;

import org.owasp.herder.exception.IncompatibleDatabaseException;
import org.owasp.herder.user.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnProperty(
    prefix = "application.runner",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Component
@Slf4j
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {
  private final UserService userService;

  private final DatabaseClient databaseClient;

  @Override
  public void run(ApplicationArguments args) {
    final String mysqlVersion =
        databaseClient
            .sql("select @@version")
            .fetch()
            .first()
            .block()
            .values()
            .iterator()
            .next()
            .toString();

    final String mysqlVersionComment =
        databaseClient
            .sql("select @@version_comment")
            .fetch()
            .first()
            .block()
            .values()
            .iterator()
            .next()
            .toString();

    if (mysqlVersionComment.substring(0, 5).equals("MySQL")) {

      final int dotPosition = mysqlVersion.indexOf('.');

      if (dotPosition < 1) {
        throw new IncompatibleDatabaseException("Could not parse MySQL version");
      }
      final String majorVersionString = mysqlVersion.substring(0, dotPosition);

      final int majorVersion = Integer.parseInt(majorVersionString);

      if (majorVersion < 8) {
        throw new IncompatibleDatabaseException("MySQL must be at least major version 8");
      }

    } else if ((mysqlVersionComment.length() >= 7)
        && (mysqlVersionComment.substring(0, 7).equals("mariadb"))) {

      final int dotPosition1 = mysqlVersion.indexOf('.');

      if (dotPosition1 < 1) {
        throw new IncompatibleDatabaseException(
            String.format("Could not parse MariaDB version %s", mysqlVersion));
      }

      final String majorVersionString = mysqlVersion.substring(0, dotPosition1);

      final int majorVersion = Integer.parseInt(majorVersionString);

      final String restOfVersion = mysqlVersion.substring(0, dotPosition1 + 1);

      final int dotPosition2 = restOfVersion.indexOf('.');

      if (dotPosition2 < 1) {
        throw new IncompatibleDatabaseException(
            String.format("Could not parse MariaDB version %s", mysqlVersion));
      }
      final String minorVersionString = restOfVersion.substring(0, dotPosition2);

      final int minorVersion = Integer.parseInt(minorVersionString);

      if ((majorVersion < 10) || (majorVersion == 10 && minorVersion < 2)) {
        throw new IncompatibleDatabaseException("MariaDB must be at least version 10.2");
      }

    } else {

      throw new IncompatibleDatabaseException(
          String.format("Only MySQL is supported, found %s", mysqlVersionComment));
    }

    log.info("Found database " + mysqlVersionComment + " version " + mysqlVersion);

    if (!userService.existsByLoginName("admin").block()) {
      final long adminId =
          userService
              .createPasswordUser(
                  "Administrator",
                  "admin",
                  "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G")
              .block();
      userService.promote(adminId).block();
    }

    if (!userService.existsByDisplayName("Test user").block()) {
      userService.create("Test user").block();
    }

    if (!userService.existsByDisplayName("Test user 2").block()) {
      userService.create("Test user 2").block();
    }

    if (!userService.existsByDisplayName("Test user 3").block()) {
      userService.create("Test user 3").block();
    }
  }
}
