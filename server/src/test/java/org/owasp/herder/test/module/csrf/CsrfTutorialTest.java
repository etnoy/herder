/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import lombok.NonNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfTutorial unit test")
class CsrfTutorialTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private static final String MODULE_NAME = "csrf-tutorial";

  CsrfTutorial csrfTutorial;

  @Mock ModuleService moduleService;

  @Mock CsrfService csrfService;

  @Mock FlagHandler flagHandler;

  final Module mockModule = mock(Module.class);

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    reset(mockModule);
    when(moduleService.create(MODULE_NAME)).thenReturn(Mono.just(mockModule));

    csrfTutorial = new CsrfTutorial(csrfService, moduleService, flagHandler);
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {

    class CsrfTutorialChild extends CsrfTutorial {

      public CsrfTutorialChild(
          CsrfService csrfService, ModuleService moduleService, FlagHandler flagHandler) {
        super(csrfService, moduleService, flagHandler);
      }

      @Override
      public boolean canEqual(Object o) {
        return false;
      }
    }

    EqualsVerifier.forClass(CsrfTutorial.class)
        .withRedefinedSuperclass()
        .withRedefinedSubclass(CsrfTutorialChild.class)
        .withIgnoredAnnotations(NonNull.class)
        .verify();
  }

  @Test
  void attack_InvalidTarget_ReturnsError() {
    final long attackerUserId = 90L;

    final String mockTarget = "abcd123";

    when(csrfService.validatePseudonym(mockTarget, MODULE_NAME)).thenReturn(Mono.just(false));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getName()).thenReturn(MODULE_NAME);

    StepVerifier.create(csrfTutorial.attack(attackerUserId, mockTarget))
        .assertNext(
            result -> {
              assertThat(result.getPseudonym()).isNull();
              assertThat(result.getFlag()).isNull();
              assertThat(result.getError()).isNotNull();
              assertThat(result.getMessage()).isNull();
            })
        .expectComplete()
        .verify();
  }

  @Test
  void attack_ValidTarget_Activates() {
    final long attackerUserId = 90L;

    final String mockTarget = "abcd123";
    final String mockAttacker = "xyz789";

    when(csrfService.getPseudonym(attackerUserId, MODULE_NAME)).thenReturn(Mono.just(mockAttacker));

    when(csrfService.validatePseudonym(mockTarget, MODULE_NAME)).thenReturn(Mono.just(true));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getName()).thenReturn(MODULE_NAME);
    when(csrfService.attack(mockTarget, MODULE_NAME)).thenReturn(Mono.empty());

    StepVerifier.create(csrfTutorial.attack(attackerUserId, mockTarget))
        .assertNext(
            result -> {
              assertThat(result.getPseudonym()).isNull();
              assertThat(result.getFlag()).isNull();
              assertThat(result.getError()).isNull();
              assertThat(result.getMessage()).isNotNull();
            })
        .expectComplete()
        .verify();
  }

  @Test
  void attack_TargetsSelf_DoesNotActivate() {
    final long userId = 90L;

    final String pseudonym = "xyz789";

    when(csrfService.getPseudonym(userId, MODULE_NAME)).thenReturn(Mono.just(pseudonym));

    when(csrfService.validatePseudonym(pseudonym, MODULE_NAME)).thenReturn(Mono.just(true));
    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getName()).thenReturn(MODULE_NAME);

    StepVerifier.create(csrfTutorial.attack(userId, pseudonym))
        .assertNext(
            result -> {
              assertThat(result.getPseudonym()).isNull();
              assertThat(result.getFlag()).isNull();
              assertThat(result.getError()).isNotNull();
              assertThat(result.getMessage()).isNull();
            })
        .expectComplete()
        .verify();
  }

  @Test
  void getTutorial_Activated_ReturnsFlag() {
    final long mockUserId = 45L;
    final String mockPseudonym = "abcd123";
    final String flag = "flag";

    when(csrfService.getPseudonym(mockUserId, MODULE_NAME)).thenReturn(Mono.just(mockPseudonym));

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getName()).thenReturn(MODULE_NAME);

    when(flagHandler.getDynamicFlag(mockUserId, MODULE_NAME)).thenReturn(Mono.just(flag));
    when(csrfService.validate(mockPseudonym, MODULE_NAME)).thenReturn(Mono.just(true));

    StepVerifier.create(csrfTutorial.getTutorial(mockUserId))
        .assertNext(
            result -> {
              assertThat(result.getPseudonym()).isEqualTo(mockPseudonym);
              assertThat(result.getFlag()).isEqualTo(flag);
            })
        .expectComplete()
        .verify();
  }

  @Test
  void getTutorial_NotActivated_ReturnsActivationLink() {
    final long mockUserId = 45L;
    final String mockPseudonym = "abcd123";

    when(csrfService.getPseudonym(mockUserId, MODULE_NAME)).thenReturn(Mono.just(mockPseudonym));

    when(mockModule.isFlagStatic()).thenReturn(false);

    when(csrfService.validate(mockPseudonym, MODULE_NAME)).thenReturn(Mono.just(false));

    StepVerifier.create(csrfTutorial.getTutorial(mockUserId))
        .assertNext(
            result -> {
              assertThat(result.getPseudonym()).isEqualTo(mockPseudonym);
              assertThat(result.getFlag()).isNull();
            })
        .expectComplete()
        .verify();
  }
}
