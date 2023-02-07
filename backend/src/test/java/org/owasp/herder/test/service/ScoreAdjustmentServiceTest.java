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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreAdjustmentService unit tests")
class ScoreAdjustmentServiceTest extends BaseTest {

  private ScoreAdjustmentService scoreAdjustmentService;

  @Mock
  ScoreAdjustmentRepository scoreAdjustmentRepository;

  @Mock
  TeamService teamService;

  @Mock
  Clock clock;

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    scoreAdjustmentService = new ScoreAdjustmentService(scoreAdjustmentRepository, teamService, clock);
  }

  @Test
  @DisplayName("Can submit score adjustment for user")
  void submitUserAdjustment_ValidUserId_ReturnsCorrection() {
    final int amount = 1000;
    final String description = "Bonus";

    when(scoreAdjustmentRepository.save(any(ScoreAdjustment.class)))
      .thenAnswer(scoreAdjustment -> Mono.just(scoreAdjustment.getArgument(0, ScoreAdjustment.class)));

    setClock(TestConstants.YEAR_2000_CLOCK);

    StepVerifier
      .create(scoreAdjustmentService.submitUserAdjustment(TestConstants.TEST_USER_ID, amount, description))
      .assertNext(scoreAdjustment -> {
        assertThat(scoreAdjustment.getUserIds()).containsExactly(TestConstants.TEST_USER_ID);
        assertThat(scoreAdjustment.getAmount()).isEqualTo(amount);
        assertThat(scoreAdjustment.getDescription()).isEqualTo(description);
        assertThat(scoreAdjustment.getTime()).isEqualTo(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK));
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can submit score adjustment for team")
  void submitTeamAdjustment_ValidUserId_ReturnsCorrection() {
    final int amount = 1000;
    final String description = "Bonus";

    when(scoreAdjustmentRepository.save(any(ScoreAdjustment.class)))
      .thenAnswer(scoreAdjustment -> Mono.just(scoreAdjustment.getArgument(0, ScoreAdjustment.class)));

    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(mockUser1.getId()).thenReturn("1");
    when(mockUser2.getId()).thenReturn("2");
    when(mockUser3.getId()).thenReturn("3");

    final ArrayList<UserEntity> members = new ArrayList<>(List.of(mockUser1, mockUser2, mockUser3));

    final TeamEntity testTeam = TestConstants.TEST_TEAM_ENTITY.withMembers(members);

    when(teamService.getById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(testTeam));

    setClock(TestConstants.YEAR_2000_CLOCK);

    StepVerifier
      .create(scoreAdjustmentService.submitTeamAdjustment(TestConstants.TEST_TEAM_ID, amount, description))
      .assertNext(scoreAdjustment -> {
        assertThat(scoreAdjustment.getUserIds()).containsExactlyInAnyOrder("1", "2", "3");
        assertThat(scoreAdjustment.getAmount()).isEqualTo(amount);
        assertThat(scoreAdjustment.getDescription()).isEqualTo(description);
        assertThat(scoreAdjustment.getTime()).isEqualTo(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK));
      })
      .verifyComplete();
  }
}
