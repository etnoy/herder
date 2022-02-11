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
package org.owasp.herder.it.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;

import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("User administration integration tests")
class UserAdministrationIT extends BaseIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("Creating a password user should succeed")
  void canCreateValidUserWithCreatePasswordUser() {
    final String displayName = "Test user";
    final String loginName = "testUser";
    final String passwordHash = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";
    final long userId =
        userService.createPasswordUser(displayName, loginName, passwordHash).block();

    StepVerifier.create(userService.findById(userId))
        .assertNext(
            user -> {
              assertThat(user.getDisplayName()).isEqualTo(displayName);
              assertThat(user.getId()).isEqualTo(userId);
            })
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("The database must handle non-latin display names")
  void canHandleNonLatinUsernames() {
    for (final String displayName : TestConstants.STRINGS) {
      if (!displayName.isEmpty()) {
        StepVerifier.create(
                userService
                    .create(displayName)
                    .flatMap(userService::findById)
                    .map(UserEntity::getDisplayName))
            .expectNext(displayName)
            .expectComplete()
            .verify();
        integrationTestUtils.resetState();
      }
    }
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();
  }
}
