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
import org.owasp.herder.scoring.ScoreAdjustment;
import org.owasp.herder.scoring.ScoreAdjustmentRepository;
import org.owasp.herder.scoring.ScoreAdjustmentService;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreAdjustmentService unit tests")
class ScoreAdjustmentServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ScoreAdjustmentService scoreAdjustmentService;

  @Mock ScoreAdjustmentRepository scoreAdjustmentRepository;

  @Mock Clock clock;

  private void setClock(final Clock clock) {
    scoreAdjustmentService.setClock(clock);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    scoreAdjustmentService = new ScoreAdjustmentService(scoreAdjustmentRepository, null);
  }

  @Test
  void submit_ValidUserId_ReturnsCorrection() throws Exception {
    final String mockUserId = "id";
    final int amount = 1000;
    final String description = "Bonus";

    when(scoreAdjustmentRepository.save(any(ScoreAdjustment.class)))
        .thenAnswer(
            scoreAdjustment -> Mono.just(scoreAdjustment.getArgument(0, ScoreAdjustment.class)));

    final Clock fixedClock =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

    setClock(fixedClock);

    StepVerifier.create(
            scoreAdjustmentService.submitUserAdjustment(mockUserId, amount, description))
        .assertNext(
            scoreAdjustment -> {
              assertThat(scoreAdjustment.getUserIds()).contains(mockUserId);
              assertThat(scoreAdjustment.getAmount()).isEqualTo(amount);
              assertThat(scoreAdjustment.getDescription()).isEqualTo(description);
              assertThat(scoreAdjustment.getTime()).isEqualTo(LocalDateTime.now(fixedClock));
            })
        .verifyComplete();
  }
}
