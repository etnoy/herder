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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.module.ModuleController;
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleController unit tests")
class ModuleControllerTest extends BaseTest {

  ModuleController moduleController;

  @Mock
  ControllerAuthentication controllerAuthentication;

  @Mock
  ModuleService moduleService;

  @Mock
  UserService userService;

  @Test
  @DisplayName("Can error if not authenticated when finding all modules by user id")
  void findAllByUserId_NotAuthenticated_ReturnsNotAuthenticatedException() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.error(new NotAuthenticatedException()));

    StepVerifier.create(moduleController.findAllByUserId()).expectError(NotAuthenticatedException.class).verify();
  }

  @Test
  @DisplayName("Can return nothing when finding list item by nonexistent locator")
  void findListItemByLocator_LocatorDoesNotExist_ReturnsNothing() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));
    when(moduleService.findListItemByUserIdAndLocator(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.empty());
    StepVerifier.create(moduleController.findListItemByLocator(TestConstants.TEST_MODULE_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can find list item by locator")
  void findListItemByLocator_LocatorExists_ReturnsModuleListItem() {
    final ModuleListItem mockModuleListItem = mock(ModuleListItem.class);
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    when(moduleService.findListItemByUserIdAndLocator(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(mockModuleListItem));

    StepVerifier
      .create(moduleController.findListItemByLocator(TestConstants.TEST_MODULE_ID))
      .expectNext(mockModuleListItem)
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    moduleController = new ModuleController(moduleService, controllerAuthentication);
  }
}
