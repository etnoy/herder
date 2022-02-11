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
package org.owasp.herder.it.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.owasp.herder.configuration.MysqlVersionChecker;
import org.owasp.herder.exception.IncompatibleDatabaseException;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import reactor.core.publisher.Hooks;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@Testcontainers
@DisplayName("MysqlVersionChecker integration tests for MySQL 5.7")
class MysqlVersionCheckerMysql57IT {
  @Autowired IntegrationTestUtils integrationTestUtils;

  @Autowired DatabaseClient databaseClient;

  MysqlVersionChecker mysqlVersionChecker;

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @SuppressWarnings("rawtypes")
  @Container
  private static final MySQLContainer mySql57Container =
      (MySQLContainer<?>) new MySQLContainer<>("mysql:5.7").withUsername("root").withReuse(true);

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.r2dbc.url",
        () ->
            "r2dbc:mysql://"
                + mySql57Container.getHost()
                + ":"
                + mySql57Container.getFirstMappedPort()
                + "/"
                + mySql57Container.getDatabaseName()
                + "?tlsVersion=TLSv1.2");
    registry.add("spring.r2dbc.username", () -> mySql57Container.getUsername());
    registry.add("spring.r2dbc.password", () -> mySql57Container.getPassword());
  }

  @Test
  void oldMysqlVersion() {
    assertThatThrownBy(() -> mysqlVersionChecker.mySqlVersionCheck())
        .isInstanceOf(IncompatibleDatabaseException.class);
  }

  @BeforeEach
  private void setUp() {
    mysqlVersionChecker = new MysqlVersionChecker(databaseClient);
  }
}
