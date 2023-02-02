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
package org.owasp.herder.test.module.flag;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.test.BaseTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagTutorial unit tests")
class FlagTutorialTest extends BaseTest {

  private String moduleLocator;

  FlagTutorial flagTutorial;

  @Mock
  ModuleService moduleService;

  @Mock
  ScoreboardService scoreboardService;

  @Mock
  FlagHandler flagHandler;

  @BeforeEach
  void setup() {
    flagTutorial = new FlagTutorial(flagHandler);

    moduleLocator = flagTutorial.getLocator();
  }

  @Test
  @DisplayName("getFlag can return flag")
  void getFlag_ValidData_ReturnsFlag() {
    final String testUserId = "id";
    final String flag = "flag";

    when(flagHandler.getDynamicFlag(testUserId, moduleLocator)).thenReturn(Mono.just(flag));

    StepVerifier.create(flagTutorial.getFlag(testUserId)).expectNext(flag).verifyComplete();
  }
}
