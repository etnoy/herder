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
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.module.flag.FlagTutorialController;
import org.owasp.herder.module.flag.FlagTutorialResult;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagTutorialController unit tests")
class FlagTutorialControllerTest extends BaseTest {

  private FlagTutorialController flagTutorialController;

  @Mock
  private FlagTutorial flagTutorial;

  @Mock
  private ControllerAuthentication controllerAuthentication;

  @Test
  @DisplayName("Can error when getting flag without being authenticated")
  void getFlag_UserNotAuthenticated_ReturnsException() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.error(new NotAuthenticatedException()));

    StepVerifier.create(flagTutorialController.getFlag()).expectError(NotAuthenticatedException.class).verify();
  }

  @BeforeEach
  void setup() {
    flagTutorialController = new FlagTutorialController(flagTutorial, controllerAuthentication);
  }

  @Test
  @DisplayName("Can submit flag")
  void submitFlag_UserAuthenticated_ReturnsFlag() {
    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(TestConstants.TEST_USER_ID));
    when(flagTutorial.getFlag(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_STATIC_FLAG));

    FlagTutorialResult testFlag = FlagTutorialResult.builder().flag(TestConstants.TEST_STATIC_FLAG).build();

    StepVerifier.create(flagTutorialController.getFlag()).expectNext(testFlag).verifyComplete();
  }
}
