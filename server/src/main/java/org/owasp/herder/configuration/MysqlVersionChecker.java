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
package org.owasp.herder.configuration;

import java.util.Map;

import org.owasp.herder.exception.IncompatibleDatabaseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnProperty(
    prefix = "application.runner",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@Configuration
@Slf4j
public class MysqlVersionChecker {
  private final DatabaseClient databaseClient;

  private String getResultsFromDatabase(final String query) {
    final Map<String, Object> versionResult = databaseClient.sql(query).fetch().first().block();

    if (versionResult == null) {
      throw new NullPointerException();
    }

    return versionResult.values().iterator().next().toString();
  }

  private void checkMySqlVersion(final String mySqlVersion) {
    final int dotPosition = mySqlVersion.indexOf('.');

    if (dotPosition < 1) {
      throw new IncompatibleDatabaseException("Could not parse MySQL version " + mySqlVersion);
    }
    final String majorVersionString = mySqlVersion.substring(0, dotPosition);

    final int majorVersion = Integer.parseInt(majorVersionString);

    if (majorVersion < 8) {
      throw new IncompatibleDatabaseException(
          "MySQL must be at least major version 8, found " + mySqlVersion);
    }
  }

  @Bean
  public void mySqlVersionCheck() {

    final String mysqlVersion = getResultsFromDatabase("select @@version");

    final String mysqlVersionComment = getResultsFromDatabase("select @@version_comment");

    if ((mysqlVersionComment.length() >= 5)
        && (mysqlVersionComment.substring(0, 5).equalsIgnoreCase("MySQL"))) {
      // MySQL detected
      checkMySqlVersion(mysqlVersion);
    } else if ((mysqlVersionComment.length() >= 7)
        && (mysqlVersionComment.substring(0, 7).equalsIgnoreCase("mariadb"))) {
      // MariaDB detected
      checkMariaDbVersion(mysqlVersion);
    } else {
      // Something else detected, not supported
      throw new IncompatibleDatabaseException(
          String.format(
              "Only MySQL > 8 and MariaDB > 10.2 are supported, found %s version %s",
              mysqlVersionComment, mysqlVersion));
    }

    log.info("Found database " + mysqlVersionComment + " version " + mysqlVersion);
  }

  private void checkMariaDbVersion(final String mysqlVersion) {
    final int dotPosition1 = mysqlVersion.indexOf('.');

    if (dotPosition1 < 1) {
      throw new IncompatibleDatabaseException(mariaDbParsingErrorMessage(mysqlVersion));
    }

    final String majorVersionString = mysqlVersion.substring(0, dotPosition1);

    final int majorVersion;

    try {
      majorVersion = Integer.parseInt(majorVersionString);
    } catch (NumberFormatException e) {
      throw new IncompatibleDatabaseException(mariaDbParsingErrorMessage(mysqlVersion));
    }

    final String restOfVersion = mysqlVersion.substring(dotPosition1 + 1);

    final int dotPosition2 = restOfVersion.indexOf('.');

    int minorVersion;

    if (dotPosition2 < 1) {
      try {
        minorVersion = Integer.parseInt(restOfVersion);
      } catch (NumberFormatException e) {
        throw new IncompatibleDatabaseException(mariaDbParsingErrorMessage(mysqlVersion));
      }
    } else {
      final String minorVersionString = restOfVersion.substring(0, dotPosition2);

      try {
        minorVersion = Integer.parseInt(minorVersionString);
      } catch (NumberFormatException e) {
        throw new IncompatibleDatabaseException(mariaDbParsingErrorMessage(mysqlVersion));
      }
    }
    if ((majorVersion < 10) || (majorVersion == 10 && minorVersion < 2)) {
      throw new IncompatibleDatabaseException(
          "MariaDB must be at least version 10.2, found " + mysqlVersion);
    }
  }

  private String mariaDbParsingErrorMessage(final String mysqlVersion) {
    return String.format("Could not parse MariaDB version %s", mysqlVersion);
  }
}
