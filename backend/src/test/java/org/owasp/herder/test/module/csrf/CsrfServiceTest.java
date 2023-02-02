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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.csrf.CsrfAttack;
import org.owasp.herder.module.csrf.CsrfAttackRepository;
import org.owasp.herder.module.csrf.CsrfService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfService unit tests")
class CsrfServiceTest extends BaseTest {

  CsrfService csrfService;

  @Mock
  CsrfAttackRepository csrfAttackRepository;

  @Mock
  FlagHandler flagHandler;

  @Mock
  Clock clock;

  @BeforeEach
  void setup() {
    csrfService = new CsrfService(csrfAttackRepository, flagHandler, clock);
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @Test
  @DisplayName("Can return nothing if an attack is not found")
  void attack_AttackNotFound_Fails() {
    when(
      csrfAttackRepository.findByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.empty());

    StepVerifier
      .create(csrfService.attack(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can perform a successful csrf attack")
  void attack_AttackIsStarted_Succeeds() {
    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(
      csrfAttackRepository.findByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.withFinished(any())).thenReturn(mockCsrfAttack);
    when(csrfAttackRepository.save(mockCsrfAttack)).thenReturn(Mono.just(mockCsrfAttack));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(csrfService.attack(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .verifyComplete();

    ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(mockCsrfAttack).withFinished(captor.capture());
    assertThat(captor.getValue()).isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
  }

  @Test
  @DisplayName("Can get the csrf pseudonym")
  void getPseudonym_ValidArguments_CallsFlagHandler() {
    final String mockFlag = "flag";

    when(flagHandler.getSaltedHmac(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, "csrfPseudonym"))
      .thenReturn(Mono.just(mockFlag));

    StepVerifier
      .create(csrfService.getPseudonym(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
      .expectNext(mockFlag)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can validate a correct csrf pseudonym")
  void validatePseudonym_ValidPseudonym_ReturnsTrue() {
    when(
      csrfAttackRepository.countByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(1L));

    StepVerifier
      .create(csrfService.validatePseudonym(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can fail an invalid csrf pseudonym")
  void validatePseudonym_InvalidPseudonym_ReturnsTrue() {
    when(
      csrfAttackRepository.countByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(0L));

    StepVerifier
      .create(csrfService.validatePseudonym(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can validate as false when initialized but not completed")
  void validate_InitializedButNotCompleted_ReturnsFalse() {
    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(
      csrfAttackRepository.findByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.getFinished()).thenReturn(null);

    when(csrfAttackRepository.save(any(CsrfAttack.class))).thenReturn(Mono.just(mockCsrfAttack));
    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(csrfService.validate(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can validate a completed csrf attack")
  void validate_AssignmentCompleted_ReturnsTrue() {
    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(
      csrfAttackRepository.findByPseudonymAndModuleLocator(
        TestConstants.TEST_CSRF_PSEUDONYM,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.getFinished()).thenReturn(LocalDateTime.MAX);

    when(csrfAttackRepository.save(any(CsrfAttack.class))).thenReturn(Mono.just(mockCsrfAttack));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(csrfService.validate(TestConstants.TEST_CSRF_PSEUDONYM, TestConstants.TEST_MODULE_ID))
      .expectNext(true)
      .verifyComplete();
  }
}
