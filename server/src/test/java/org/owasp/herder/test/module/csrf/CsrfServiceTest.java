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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeAll;
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

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsrfService unit tests")
class CsrfServiceTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  CsrfService csrfService;

  @Mock CsrfAttackRepository csrfAttackRepository;

  @Mock FlagHandler flagHandler;

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    csrfService = new CsrfService(csrfAttackRepository, flagHandler);
  }

  @Test
  void attack_RepositoryError_ReturnsError() {
    final String mockTarget = "abcd123";
    final String mockModuleName = "csrf-module";

    when(csrfAttackRepository.findByPseudonymAndModuleName(mockTarget, mockModuleName))
        .thenReturn(Mono.error(new IOException()));

    StepVerifier.create(csrfService.attack(mockTarget, mockModuleName))
        .expectError(IOException.class)
        .verify();
  }

  private void setClock(final Clock clock) {
    csrfService.setClock(clock);
  }

  @Test
  void attack_AttackNotFound_Fails() {
    final String mockTarget = "abcd123";
    final String mockModuleName = "csrf-module";

    when(csrfAttackRepository.findByPseudonymAndModuleName(mockTarget, mockModuleName))
        .thenReturn(Mono.empty());

    StepVerifier.create(csrfService.attack(mockTarget, mockModuleName)).expectComplete().verify();
  }

  @Test
  void attack_AttackIsStarted_Succeeds() {
    final String mockTarget = "abcd123";
    final String mockModuleName = "csrf-module";

    final Clock fixedClock =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(csrfAttackRepository.findByPseudonymAndModuleName(mockTarget, mockModuleName))
        .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.withFinished(LocalDateTime.now(fixedClock))).thenReturn(mockCsrfAttack);

    when(csrfAttackRepository.save(mockCsrfAttack)).thenReturn(Mono.just(mockCsrfAttack));

    setClock(fixedClock);

    StepVerifier.create(csrfService.attack(mockTarget, mockModuleName)).expectComplete().verify();

    ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(mockCsrfAttack).withFinished(captor.capture());
    assertThat(captor.getValue()).isEqualTo(LocalDateTime.now(fixedClock));
  }

  @Test
  void getPseudonym_ValidArguments_CallsFlagHandler() {
    // TODO: check what happens with bad arguments
    final String mockUserId = "id";
    final String mockModuleName = "csrf-module";
    final String mockFlag = "flag";

    when(flagHandler.getSaltedHmac(mockUserId, mockModuleName, "csrfPseudonym"))
        .thenReturn(Mono.just(mockFlag));

    StepVerifier.create(csrfService.getPseudonym(mockUserId, mockModuleName))
        .expectNext(mockFlag)
        .expectComplete()
        .verify();
  }

  @Test
  void validatePseudonym_ValidPseudonym_ReturnsTrue() {
    // TODO: check what happens with bad arguments
    final String mockPseudonym = "abc123";
    final String mockModuleName = "csrf-module";

    when(csrfAttackRepository.countByPseudonymAndModuleName(mockPseudonym, mockModuleName))
        .thenReturn(Mono.just(1L));

    StepVerifier.create(csrfService.validatePseudonym(mockPseudonym, mockModuleName))
        .expectNext(true)
        .expectComplete()
        .verify();
  }

  @Test
  void validatePseudonym_InvalidPseudonym_ReturnsTrue() {
    // TODO: check what happens with bad arguments
    final String mockPseudonym = "abc123";
    final String mockModuleName = "csrf-module";

    when(csrfAttackRepository.countByPseudonymAndModuleName(mockPseudonym, mockModuleName))
        .thenReturn(Mono.just(0L));

    StepVerifier.create(csrfService.validatePseudonym(mockPseudonym, mockModuleName))
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  void validate_InitializedButNotCompleted_ReturnsFalse() {
    // TODO: check what happens with bad arguments
    final String mockPseudonym = "abc123";
    final String mockModuleName = "csrf-module";
    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(csrfAttackRepository.findByPseudonymAndModuleName(mockPseudonym, mockModuleName))
        .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.getFinished()).thenReturn(null);

    when(csrfAttackRepository.save(any(CsrfAttack.class))).thenReturn(Mono.just(mockCsrfAttack));
    StepVerifier.create(csrfService.validate(mockPseudonym, mockModuleName))
        .expectNext(false)
        .expectComplete()
        .verify();
  }

  @Test
  void validate_AssignmentCompleted_ReturnsTrue() {
    // TODO: check what happens with bad arguments
    final String mockPseudonym = "abc123";
    final String mockModuleName = "csrf-module";
    final CsrfAttack mockCsrfAttack = mock(CsrfAttack.class);

    when(csrfAttackRepository.findByPseudonymAndModuleName(mockPseudonym, mockModuleName))
        .thenReturn(Mono.just(mockCsrfAttack));

    when(mockCsrfAttack.getFinished()).thenReturn(LocalDateTime.MAX);

    when(csrfAttackRepository.save(any(CsrfAttack.class))).thenReturn(Mono.just(mockCsrfAttack));
    StepVerifier.create(csrfService.validate(mockPseudonym, mockModuleName))
        .expectNext(true)
        .expectComplete()
        .verify();
  }
}
