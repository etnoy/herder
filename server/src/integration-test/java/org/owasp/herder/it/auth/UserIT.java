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
package org.owasp.herder.it.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.User;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("User integration test")
class UserIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired UserService userService;

  @Autowired TestUtils testService;

  @Test
  void createPasswordUser_ValidData_ReturnsCorrectUser() {
    final long userId =
        userService
            .createPasswordUser(
                "Test User",
                "user_login_name",
                "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW")
            .block();

    StepVerifier.create(userService.findById(userId).map(User::getId))
        .expectNext(userId)
        .expectComplete()
        .verify();
  }

  @Test
  void createUser_NaughtyUsernames_RepositoryFindsCorrectUser() {
    for (final String displayName : TestUtils.STRINGS) {
      if (!displayName.isEmpty()) {
        StepVerifier.create(
                userService
                    .create(displayName)
                    .flatMap(userService::findById)
                    .map(User::getDisplayName))
            .expectNext(displayName)
            .expectComplete()
            .verify();
        testService.deleteAll().block();
      }
    }
  }

  @BeforeEach
  private void setUp() {
    testService.deleteAll().block();
  }
}
