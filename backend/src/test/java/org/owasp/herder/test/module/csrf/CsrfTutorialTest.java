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
package org.owasp.herder.test.module.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.csrf.CsrfService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.test.BaseTest;
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

  final ModuleEntity mockModule = mock(ModuleEntity.class);

  @BeforeEach
  void setup() {
    // Set up the system under test
    csrfTutorial = new CsrfTutorial(csrfService, flagHandler);
    locator = csrfTutorial.getLocator();
  }

  @Test
  void attack_InvalidTarget_ReturnsError() {
    final String attackerUserId = "id";

    final String mockTarget = "abcd123";

    when(csrfService.validatePseudonym(mockTarget, locator))
      .thenReturn(Mono.just(false));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getLocator()).thenReturn(locator);

    StepVerifier
      .create(csrfTutorial.attack(attackerUserId, mockTarget))
      .assertNext(
        result -> {
          assertThat(result.getPseudonym()).isNull();
          assertThat(result.getFlag()).isNull();
          assertThat(result.getError()).isNotNull();
          assertThat(result.getMessage()).isNull();
        }
      )
      .verifyComplete();
  }

  @Test
  void attack_ValidTarget_Activates() {
    final String attackerUserId = "id";

    final String mockTarget = "abcd123";
    final String mockAttacker = "xyz789";

    when(csrfService.getPseudonym(attackerUserId, locator))
      .thenReturn(Mono.just(mockAttacker));
    when(csrfService.validatePseudonym(mockTarget, locator))
      .thenReturn(Mono.just(true));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getLocator()).thenReturn(locator);
    when(csrfService.attack(mockTarget, locator)).thenReturn(Mono.empty());

    StepVerifier
      .create(csrfTutorial.attack(attackerUserId, mockTarget))
      .assertNext(
        result -> {
          assertThat(result.getPseudonym()).isNull();
          assertThat(result.getFlag()).isNull();
          assertThat(result.getError()).isNull();
          assertThat(result.getMessage()).isNotNull();
        }
      )
      .verifyComplete();
  }

  @Test
  void attack_TargetsSelf_DoesNotActivate() {
    final String userId = "id";

    final String pseudonym = "xyz789";

    when(csrfService.getPseudonym(userId, locator))
      .thenReturn(Mono.just(pseudonym));
    when(csrfService.validatePseudonym(pseudonym, locator))
      .thenReturn(Mono.just(true));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getLocator()).thenReturn(locator);

    StepVerifier
      .create(csrfTutorial.attack(userId, pseudonym))
      .assertNext(
        result -> {
          assertThat(result.getPseudonym()).isNull();
          assertThat(result.getFlag()).isNull();
          assertThat(result.getError()).isNotNull();
          assertThat(result.getMessage()).isNull();
        }
      )
      .verifyComplete();
  }

  @Test
  void getTutorial_Activated_ReturnsFlag() {
    final String mockUserId = "id";
    final String mockPseudonym = "abcd123";
    final String flag = "flag";

    when(csrfService.getPseudonym(mockUserId, locator))
      .thenReturn(Mono.just(mockPseudonym));

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getName()).thenReturn(locator);

    when(flagHandler.getDynamicFlag(mockUserId, locator))
      .thenReturn(Mono.just(flag));
    when(csrfService.validate(mockPseudonym, locator))
      .thenReturn(Mono.just(true));

    StepVerifier
      .create(csrfTutorial.getTutorial(mockUserId))
      .assertNext(
        result -> {
          assertThat(result.getPseudonym()).isEqualTo(mockPseudonym);
          assertThat(result.getFlag()).isEqualTo(flag);
        }
      )
      .verifyComplete();
  }

  @Test
  void getTutorial_NotActivated_ReturnsActivationLink() {
    final String mockUserId = "id";
    final String mockPseudonym = "abcd123";
    final Mono<String> flag = Mono.just("flag");

    when(csrfService.getPseudonym(mockUserId, locator))
      .thenReturn(Mono.just(mockPseudonym));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(csrfService.validate(mockPseudonym, locator))
      .thenReturn(Mono.just(false));
    when(flagHandler.getDynamicFlag(mockUserId, locator)).thenReturn(flag);

    StepVerifier
      .create(csrfTutorial.getTutorial(mockUserId))
      .assertNext(
        result -> {
          assertThat(result.getPseudonym()).isEqualTo(mockPseudonym);
          assertThat(result.getFlag()).isNull();
        }
      )
      .verifyComplete();
  }
}
