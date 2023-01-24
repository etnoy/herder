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
package org.owasp.herder.it.validation;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserController;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

@DisplayName("Can reject an invalid entity id")
class EntityIdValidationIT extends BaseIT {

  private static final String ID_NOT_NULL = "id must not be null";

  private static final String INVALID_ID_SIZE = "id must be 24 characters long";

  private static final String INVALID_ID_PATTERN = "id can only contain hexadecimal characters";

  static Stream<Arguments> invalidIdSource() {
    return Stream.of(
      arguments(null, ID_NOT_NULL),
      arguments("", INVALID_ID_SIZE),
      arguments("a", INVALID_ID_SIZE),
      arguments("abcdef", INVALID_ID_SIZE),
      arguments("abc123abc123abc123a", INVALID_ID_SIZE),
      arguments("abc123abc123abc123abc", INVALID_ID_SIZE),
      arguments("Abc123abc123abc123", INVALID_ID_PATTERN),
      arguments("gbc123abc123abc123", INVALID_ID_PATTERN),
      arguments("abc12-abc123abc123", INVALID_ID_PATTERN),
      arguments("åbc123abc123abc123", INVALID_ID_PATTERN),
      arguments("ab 123abc123abc123", INVALID_ID_PATTERN)
    );
  }

  @Autowired
  ModuleService moduleService;

  @Autowired
  UserService userService;

  @Autowired
  UserController userController;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.close()")
  void moduleService_create(final String moduleId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> moduleService.close(moduleId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.findById()")
  void moduleService_findById(final String moduleId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> moduleService.findById(moduleId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.findByLocatorWithSolutionStatus()")
  void moduleService_findByLocatorWithSolutionStatus(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.findListItemByLocator(userId, TestConstants.TEST_MODULE_LOCATOR),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.open()")
  void moduleService_open(final String moduleId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> moduleService.open(moduleId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.setDynamicFlag()")
  void moduleService_setDynamicFlag(final String moduleId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> moduleService.setDynamicFlag(moduleId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in moduleService.setStaticFlag()")
  void moduleService_setStaticFlag(final String moduleId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.setStaticFlag(moduleId, TestConstants.TEST_STATIC_FLAG),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.deleteById()")
  void userService_deleteById(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.delete(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.demote()")
  void userService_demote(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.demote(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.disable()")
  void userService_disable(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.disable(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.enable()")
  void userService_enable(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.enable(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.findById()")
  void userService_findById(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.findById(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.findKeyById()")
  void userService_findKeyById(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.findKeyById(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.findPasswordAuthByUserId()")
  void userService_findPasswordAuthByUserId(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.findPasswordAuthByUserId(userId),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.kick()")
  void userService_kick(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.kick(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.promote()")
  void userService_promote(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.promote(userId), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.setClassId()")
  void userService_setClassId_userId(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.setClassId(userId, TestConstants.TEST_CLASS_ID),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.setDisplayName()")
  void userService_setDisplayName(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.setDisplayName(userId, TestConstants.TEST_USER_DISPLAY_NAME),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.suspendUntil()")
  void userService_suspendUntil_DateTime(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.suspendUntil(userId, LocalDateTime.now()),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.suspendUntil()")
  void userService_suspendUntil_DateTimeMessage(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.suspendUntil(userId, LocalDateTime.now(), "Banned!"),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.suspendUntil()")
  void userService_suspendUntil_Duration(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.suspendForDuration(userId, Duration.ofDays(14)),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidIdSource")
  @DisplayName("in userService.suspendUntil()")
  void userService_suspendUntil_DurationMessage(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> userService.suspendForDuration(userId, Duration.ofDays(14), "Banned!"),
      containingMessage
    );
  }

  @ParameterizedTest
  @WithMockUser
  @MethodSource("invalidIdSource")
  @DisplayName("in userController.deleteById()")
  void userController_deleteById(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.delete(userId), containingMessage);
  }

  @ParameterizedTest
  @WithMockUser
  @MethodSource("invalidIdSource")
  @DisplayName("in userController.findById()")
  void userController_findById(final String userId, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(() -> userService.findById(userId), containingMessage);
  }
}
