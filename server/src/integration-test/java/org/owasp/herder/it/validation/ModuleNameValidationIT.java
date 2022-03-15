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

@DisplayName("Can reject an invalid module name")
class ModuleNameValidationIT extends BaseIT {
  private static final String MODULE_NAME_TOO_LONG =
      "Module name must not be longer than 80 characters";

  private static final String NULL_MODULE_NAME = "Module name must not be null";

  private static final String MODULE_NAME_TOO_SHORT =
      "Module name must be at least 2 characters long";

  static Stream<Arguments> invalidModuleNameSource() {
    return Stream.of(
        arguments("", MODULE_NAME_TOO_SHORT),
        arguments(null, NULL_MODULE_NAME),
        arguments(TestConstants.VERY_LONG_STRING, MODULE_NAME_TOO_LONG));
  }

  @Autowired ModuleService moduleService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @ParameterizedTest
  @MethodSource("invalidModuleNameSource")
  @DisplayName("in moduleService.create()")
  void moduleService_create(final String moduleName, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
        () -> moduleService.create(moduleName, TestConstants.TEST_MODULE_LOCATOR),
        containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidModuleNameSource")
  @DisplayName("in moduleService.doesNotExistByName()")
  void moduleService_doesNotExistByName(final String moduleName, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
        () -> moduleService.existsByName(moduleName), containingMessage);
  }

  @ParameterizedTest
  @MethodSource("invalidModuleNameSource")
  @DisplayName("in moduleService.findAllTagsByModuleNameAndTagName()")
  void moduleService_findAllTagsByModuleNameAndTagName(
      final String moduleName, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
        () ->
            moduleService.findAllTagsByModuleNameAndTagName(
                moduleName, TestConstants.TEST_MODULE_TAG_NAME),
        containingMessage);
  }
}
