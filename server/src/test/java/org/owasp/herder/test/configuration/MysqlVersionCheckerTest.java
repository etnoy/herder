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
package org.owasp.herder.test.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.configuration.MysqlVersionChecker;
import org.owasp.herder.exception.IncompatibleDatabaseException;
import org.springframework.r2dbc.core.DatabaseClient;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("MysqlVersionChecker unit tests")
class MysqlVersionCheckerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private MysqlVersionChecker mysqlVersionChecker;

  @Mock private DatabaseClient databaseClient;

  private void setVersions(final String comment, final String version) {

    Map<String, Object> versionMap = new HashMap<>();
    versionMap.put("version", version);

    when(databaseClient.sql("select @@version").fetch().first()).thenReturn(Mono.just(versionMap));

    Map<String, Object> commentMap = new HashMap<>();
    commentMap.put("comment", comment);

    when(databaseClient.sql("select @@version_comment").fetch().first())
        .thenReturn(Mono.just(commentMap));

    // Supply the new deep stubs
    mysqlVersionChecker = new MysqlVersionChecker(databaseClient);
  }

  @Test
  void mySqlVersionCheck_Mysql8028_Supported() {
    setVersions("MySQL", "8.0.28");
    assertDoesNotThrow(() -> mysqlVersionChecker.mySqlVersionCheck());
  }

  @Test
  void mySqlVersionCheck_MariaDB106_Supported() {
    setVersions("MariaDB", "10.6");
    assertDoesNotThrow(() -> mysqlVersionChecker.mySqlVersionCheck());
  }

  @Test
  void mySqlVersionCheck_MariaDB10ADot_NotSupported() {
    setVersions("mariadb", "10.A.");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MariaDBA1_NotSupported() {
    setVersions("mariadb", "A.1");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MariaDB10A_NotSupported() {
    setVersions("mariadb", "10.A");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MariaDBEmptyVersion_NotSupported() {
    setVersions("mariadb", "");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MysqlEmptyVersion_NotSupported() {
    setVersions("MySql", "");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_EmptyComment_NotSupported() {
    setVersions("", "12345");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_Oracle_NotSupported() {
    setVersions("Oracle", "12345");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MariaDB55_NotSupported() {
    setVersions("MariaDB", "5.5");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_MariaDB101_NotSupported() {
    setVersions("MariaDB", "10.1");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @Test
  void mySqlVersionCheck_Mysql5735_NotSupported() {
    setVersions("MySQL", "5.7.35");

    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    databaseClient = mock(DatabaseClient.class, Mockito.RETURNS_DEEP_STUBS);

    mysqlVersionChecker = new MysqlVersionChecker(databaseClient);
  }
}
