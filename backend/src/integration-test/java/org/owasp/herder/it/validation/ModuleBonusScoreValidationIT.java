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

import java.util.ArrayList;
import java.util.List;
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

@DisplayName("Can reject an invalid module bonus score")
class ModuleBonusScoreValidationIT extends BaseIT {

  private static final String MODULE_BONUS_SCORES_EMPTY = "Module bonus scores cannot be empty";
  private static final String MODULE_BONUS_SCORE_NULL = "Module bonus score cannot be null";
  private static final String MODULE_BONUS_SCORE_NEGATIVE = "Module bonus score cannot be negative";

  static Stream<Arguments> invalidModuleBonusScoresSource() {
    List<Integer> nullContainingList = new ArrayList<Integer>();
    nullContainingList.add(1);
    nullContainingList.add(2);
    nullContainingList.add(null);

    return Stream.of(
      arguments(new ArrayList<Integer>(), MODULE_BONUS_SCORES_EMPTY),
      arguments(nullContainingList, MODULE_BONUS_SCORE_NULL),
      arguments(List.of(1, 2, -3), MODULE_BONUS_SCORE_NEGATIVE)
    );
  }

  @Autowired
  ModuleService moduleService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @ParameterizedTest
  @MethodSource("invalidModuleBonusScoresSource")
  @DisplayName("in moduleService.setBonusScores()")
  void moduleService_setBonusScores(final List<Integer> baseScores, final String containingMessage) {
    integrationTestUtils.checkConstraintViolation(
      () -> moduleService.setBonusScores(TestConstants.TEST_MODULE_ID, baseScores),
      containingMessage
    );
  }
}
