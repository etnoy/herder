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
package org.owasp.herder.test.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.user.User;
import org.owasp.herder.user.UserController;
import org.owasp.herder.user.UserService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController unit test")
class UserControllerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private UserController userController;

  @Mock private UserService userService;

  @Test
  void deleteById_ValidId_CallsUserService() {
    final long mockUserId = 317L;
    when(userService.deleteById(mockUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userController.deleteById(mockUserId)).expectComplete().verify();
    verify(userService, times(1)).deleteById(mockUserId);
  }

  @Test
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userService.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(userController.findAll()).expectComplete().verify();
    verify(userService, times(1)).findAll();
  }

  @Test
  void findById_UserIdDoesNotExist_ReturnsUser() {
    final long mockUserId = 559L;
    when(userService.findById(mockUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userController.findById(mockUserId)).expectComplete().verify();
    verify(userService, times(1)).findById(mockUserId);
  }

  @Test
  void findById_UserIdExists_ReturnsUser() {
    final long mockUserId = 380L;
    final User user = mock(User.class);

    when(userService.findById(mockUserId)).thenReturn(Mono.just(user));
    StepVerifier.create(userController.findById(mockUserId))
        .expectNext(user)
        .expectComplete()
        .verify();
    verify(userService, times(1)).findById(mockUserId);
  }

  @Test
  void findAll_UsersExist_ReturnsUsers() {
    final User user1 = mock(User.class);
    final User user2 = mock(User.class);
    final User user3 = mock(User.class);
    final User user4 = mock(User.class);

    when(userService.findAll()).thenReturn(Flux.just(user1, user2, user3, user4));
    StepVerifier.create(userController.findAll())
        .expectNext(user1)
        .expectNext(user2)
        .expectNext(user3)
        .expectNext(user4)
        .expectComplete()
        .verify();
    verify(userService, times(1)).findAll();
  }

  @BeforeEach
  private void setUp() throws Exception {
    // Set up the system under test
    userController = new UserController(userService);
  }
}
