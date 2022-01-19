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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.scoring.Correction;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.CorrectionService;
import org.owasp.herder.test.util.TestUtils;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("CorrectionService unit tests")
class CorrectionServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private CorrectionService correctionService;

  @Mock CorrectionRepository correctionRepository;

  @Mock Clock clock;

  private void setClock(final Clock clock) {
    correctionService.setClock(clock);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    correctionService = new CorrectionService(correctionRepository);
  }

  @Test
  void submit_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestUtils.INVALID_IDS) {
      StepVerifier.create(correctionService.submit(userId, 500, ""))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void submit_ValidUserId_ReturnsCorrection() throws Exception {
    final long mockUserId = 609L;
    final int amount = 1000;
    final String description = "Bonus";

    when(correctionRepository.save(any(Correction.class)))
        .thenAnswer(correction -> Mono.just(correction.getArgument(0, Correction.class)));

    final Clock fixedClock = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

    setClock(fixedClock);

    StepVerifier.create(correctionService.submit(mockUserId, amount, description))
        .assertNext(
            correction -> {
              assertThat(correction.getUserId()).isEqualTo(mockUserId);
              assertThat(correction.getAmount()).isEqualTo(amount);
              assertThat(correction.getDescription()).isEqualTo(description);
              assertThat(correction.getTime()).isEqualTo(LocalDateTime.now(fixedClock));
            })
        .expectComplete()
        .verify();
  }
}
