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
package org.owasp.herder.test.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolationException;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.SolverEntity;
import org.owasp.herder.user.UserController;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController unit tests")
class UserControllerTest extends BaseTest {

  private UserController userController;

  @Mock
  private UserService userService;

  @Test
  @DisplayName("Can delete user by id")
  void deleteById_ValidId_CallsUserService() {
    when(userService.delete(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userController.deleteById(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can find zero solvers")
  void findAllSolvers_NoSolversExist_CallsUserService() {
    when(userService.findAllSolvers()).thenReturn(Flux.empty());
    StepVerifier.create(userController.findAllSolvers()).verifyComplete();
  }

  @Test
  @DisplayName("Can find all solvers")
  void findAllSolvers_UsersAndTeamsExist_ReturnsUsers() {
    final SolverEntity user1 = mock(SolverEntity.class);
    final SolverEntity user2 = mock(SolverEntity.class);
    final SolverEntity team1 = mock(SolverEntity.class);
    final SolverEntity user3 = mock(SolverEntity.class);
    final SolverEntity user4 = mock(SolverEntity.class);
    final SolverEntity team2 = mock(SolverEntity.class);

    when(userService.findAllSolvers()).thenReturn(Flux.just(user1, user2, team1, user3, user4, team2));
    StepVerifier
      .create(userController.findAllSolvers())
      .recordWith(HashSet::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(users ->
        assertThat(users).containsExactlyInAnyOrder(user1, user2, team1, user3, user4, team2)
      )
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find zero users")
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userService.findAllUsers()).thenReturn(Flux.empty());
    StepVerifier.create(userController.findAll()).verifyComplete();
  }

  @Test
  @DisplayName("Can find all users")
  void findAll_UsersExist_ReturnsUsers() {
    final UserEntity user1 = mock(UserEntity.class);
    final UserEntity user2 = mock(UserEntity.class);
    final UserEntity user3 = mock(UserEntity.class);
    final UserEntity user4 = mock(UserEntity.class);

    when(userService.findAllUsers()).thenReturn(Flux.just(user1, user2, user3, user4));
    StepVerifier
      .create(userController.findAll())
      .recordWith(HashSet::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(users -> assertThat(users).containsExactlyInAnyOrder(user1, user2, user3, user4))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error when finding user by an invalid id")
  void getById_InvalidUserId_ReturnsUserNotFoundException() {
    final ConstraintViolationException mockConstraintViolation = mock(ConstraintViolationException.class);
    final String testMessage = "Invalid id";
    when(mockConstraintViolation.getMessage()).thenReturn(testMessage);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenThrow(mockConstraintViolation);
    StepVerifier
      .create(userController.getById(TestConstants.TEST_USER_ID))
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can error when finding nonexistent user by id")
  void getById_UserIdDoesNotExist_ReturnsUserNotFoundException() {
    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.error(new UserNotFoundException()));
    StepVerifier
      .create(userController.getById(TestConstants.TEST_USER_ID))
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can get user by id")
  void getById_UserIdExists_ReturnsUser() {
    final UserEntity mockUser = mock(UserEntity.class);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    StepVerifier.create(userController.getById(TestConstants.TEST_USER_ID)).expectNext(mockUser).verifyComplete();
  }

  @BeforeEach
  void setup() {
    userController = new UserController(userService);
  }
}
