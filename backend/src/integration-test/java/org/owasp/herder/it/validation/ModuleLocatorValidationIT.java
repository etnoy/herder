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
package org.owasp.herder.it.validation;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Can reject an invalid module locator")
class ModuleLocatorValidationIT extends BaseIT {

  private static final String LOCATOR_IS_NULL =
    "Module locator must not be null";
  private static final String LOCATOR_INVALID_PATTERN =
    "Module locator can only contain lowercase alphanumeric text and hyphens";
  private static final String LOCATOR_TOO_SHORT =
    "Module locator must be at least 2 characters long";
  private static final String LOCATOR_TOO_LONG =
    "Module locator must not be longer than 80 characters";

  static Stream<Arguments> invalidModuleLocatorSource() {
    return Stream.of(
      arguments("", LOCATOR_TOO_SHORT),
      arguments("a", LOCATOR_TOO_SHORT),
      arguments(null, LOCATOR_IS_NULL),
      arguments("invalid locator", LOCATOR_INVALID_PATTERN),
      arguments("invalid-löcator", LOCATOR_INVALID_PATTERN),
      arguments("INVALID-locator", LOCATOR_INVALID_PATTERN),
      arguments("invalid_locator", LOCATOR_INVALID_PATTERN),
      arguments(TestConstants.VERY_LONG_STRING, LOCATOR_TOO_LONG)
    );
  }

  @Autowired
  ModuleService moduleService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @ParameterizedTest
  @MethodSource("invalidModuleLocatorSource")
  @DisplayName("in moduleService.findByLocatorWithSolutionStatus()")
  void findByLocatorWithSolutionStatus(
    final String moduleLocator,
    final String containingMessage
  ) {
    integrationTestUtils.checkConstraintViolation(
      () ->
        moduleService.findListItemByLocator(
          TestConstants.TEST_USER_ID,
          moduleLocator
        ),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidModuleLocatorSource")
  @DisplayName("in moduleService.create()")
  void moduleService_create(
    final String moduleLocator,
    final String containingMessage
  ) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.create(TestConstants.TEST_MODULE_NAME, moduleLocator),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidModuleLocatorSource")
  @DisplayName("in moduleService.existsByLocator()")
  void moduleService_existsByLocator(
    final String moduleLocator,
    final String containingMessage
  ) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.existsByLocator(moduleLocator),
      containingMessage
    );
  }

  @ParameterizedTest
  @MethodSource("invalidModuleLocatorSource")
  @DisplayName("in moduleService.findByLocator()")
  void moduleService_findByLocator(
    final String moduleLocator,
    final String containingMessage
  ) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.findByLocator(moduleLocator),
      containingMessage
    );
  }
}
