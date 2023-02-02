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
package org.owasp.herder.test.module.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.csrf.CsrfService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfTutorial unit tests")
class CsrfTutorialTest extends BaseTest {

  String locator;

  CsrfTutorial csrfTutorial;

  @Mock
  CsrfService csrfService;

  @Mock
  FlagHandler flagHandler;

  @BeforeEach
  void setup() {
    // Set up the system under test
    csrfTutorial = new CsrfTutorial(csrfService, flagHandler);
    locator = csrfTutorial.getLocator();
  }

  @Test
  @DisplayName("Can error when attacking an invalid target")
  void attack_InvalidTarget_ReturnsError() {
    when(csrfService.validatePseudonym(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.just(false));

    StepVerifier
      .create(csrfTutorial.attack(TestConstants.TEST_USER_ID, TestConstants.TEST_CSRF_PSEUDONYM))
      .assertNext(result -> {
        assertThat(result.getPseudonym()).isNull();
        assertThat(result.getFlag()).isNull();
        assertThat(result.getError()).isEqualTo("Unknown target ID");
        assertThat(result.getMessage()).isNull();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can activate when attacking a valid target")
  void attack_ValidTarget_Activates() {
    final String mockAttacker = "xyz789";

    when(csrfService.getPseudonym(TestConstants.TEST_USER_ID, locator)).thenReturn(Mono.just(mockAttacker));
    when(csrfService.validatePseudonym(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.just(true));
    when(csrfService.attack(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.empty());

    StepVerifier
      .create(csrfTutorial.attack(TestConstants.TEST_USER_ID, TestConstants.TEST_CSRF_PSEUDONYM))
      .assertNext(result -> {
        assertThat(result.getPseudonym()).isNull();
        assertThat(result.getFlag()).isNull();
        assertThat(result.getError()).isNull();
        assertThat(result.getMessage()).isNotNull();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can deny an attack on self")
  void attack_TargetsSelf_DoesNotActivate() {
    when(csrfService.getPseudonym(TestConstants.TEST_USER_ID, locator))
      .thenReturn(Mono.just(TestConstants.TEST_CSRF_PSEUDONYM));
    when(csrfService.validatePseudonym(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.just(true));

    StepVerifier
      .create(csrfTutorial.attack(TestConstants.TEST_USER_ID, TestConstants.TEST_CSRF_PSEUDONYM))
      .assertNext(result -> {
        assertThat(result.getPseudonym()).isNull();
        assertThat(result.getFlag()).isNull();
        assertThat(result.getError()).isNotNull();
        assertThat(result.getMessage()).isNull();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return flag when activated")
  void getTutorial_Activated_ReturnsFlag() {
    when(csrfService.getPseudonym(TestConstants.TEST_USER_ID, locator))
      .thenReturn(Mono.just(TestConstants.TEST_CSRF_PSEUDONYM));

    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, locator))
      .thenReturn(Mono.just(TestConstants.TEST_DYNAMIC_FLAG));
    when(csrfService.validate(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.just(true));

    StepVerifier
      .create(csrfTutorial.getTutorial(TestConstants.TEST_USER_ID))
      .assertNext(result -> {
        assertThat(result.getPseudonym()).isEqualTo(TestConstants.TEST_CSRF_PSEUDONYM);
        assertThat(result.getFlag()).isEqualTo(TestConstants.TEST_DYNAMIC_FLAG);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return activation link when not activated")
  void getTutorial_NotActivated_ReturnsActivationLink() {
    when(csrfService.getPseudonym(TestConstants.TEST_USER_ID, locator))
      .thenReturn(Mono.just(TestConstants.TEST_CSRF_PSEUDONYM));
    when(csrfService.validate(TestConstants.TEST_CSRF_PSEUDONYM, locator)).thenReturn(Mono.just(false));
    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, locator))
      .thenReturn(Mono.just(TestConstants.TEST_DYNAMIC_FLAG));

    StepVerifier
      .create(csrfTutorial.getTutorial(TestConstants.TEST_USER_ID))
      .assertNext(result -> {
        assertThat(result.getPseudonym()).isEqualTo(TestConstants.TEST_CSRF_PSEUDONYM);
        assertThat(result.getFlag()).isNull();
      })
      .verifyComplete();
  }
}
