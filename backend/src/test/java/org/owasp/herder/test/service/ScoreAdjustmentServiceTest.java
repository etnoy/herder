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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.ScoreAdjustment;
import org.owasp.herder.scoring.ScoreAdjustmentRepository;
import org.owasp.herder.scoring.ScoreAdjustmentService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreAdjustmentService unit tests")
class ScoreAdjustmentServiceTest extends BaseTest {

  private ScoreAdjustmentService scoreAdjustmentService;

  @Mock
  ScoreAdjustmentRepository scoreAdjustmentRepository;

  @Mock
  UserService userService;

  @Mock
  Clock clock;

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    scoreAdjustmentService = new ScoreAdjustmentService(scoreAdjustmentRepository, userService, clock);
  }

  @Test
  void submit_ValidUserId_ReturnsCorrection() {
    final String mockUserId = "id";
    final int amount = 1000;
    final String description = "Bonus";

    when(scoreAdjustmentRepository.save(any(ScoreAdjustment.class)))
      .thenAnswer(scoreAdjustment -> Mono.just(scoreAdjustment.getArgument(0, ScoreAdjustment.class)));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(scoreAdjustmentService.submitUserAdjustment(mockUserId, amount, description))
      .assertNext(scoreAdjustment -> {
        assertThat(scoreAdjustment.getUserIds()).contains(mockUserId);
        assertThat(scoreAdjustment.getAmount()).isEqualTo(amount);
        assertThat(scoreAdjustment.getDescription()).isEqualTo(description);
        assertThat(scoreAdjustment.getTime()).isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
      })
      .verifyComplete();
  }
}
