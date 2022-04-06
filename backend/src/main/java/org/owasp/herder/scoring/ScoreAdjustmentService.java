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
package org.owasp.herder.scoring;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;
import org.owasp.herder.scoring.ScoreAdjustment.ScoreAdjustmentBuilder;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

@Service
@Validated
public class ScoreAdjustmentService {
  private final ScoreAdjustmentRepository scoreAdjustmentRepository;

  private final UserService userService;

  private Clock clock;

  public ScoreAdjustmentService(
      final ScoreAdjustmentRepository scoreAdjustmentRepository, final UserService userService) {
    this.scoreAdjustmentRepository = scoreAdjustmentRepository;
    this.userService = userService;
    resetClock();
  }

  public void resetClock() {
    this.clock = Clock.systemDefaultZone();
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public Mono<ScoreAdjustment> submitUserAdjustment(
      @ValidUserId final String userId, final long amount, final String description) {
    final ScoreAdjustmentBuilder scoreAdjustmentBuilder = ScoreAdjustment.builder();

    final ArrayList<String> userIds = new ArrayList<>();

    userIds.add(userId);

    scoreAdjustmentBuilder.userIds(userIds);
    scoreAdjustmentBuilder.amount(amount);
    scoreAdjustmentBuilder.description(description);
    scoreAdjustmentBuilder.time(LocalDateTime.now(clock));

    return scoreAdjustmentRepository.save(scoreAdjustmentBuilder.build());
  }

  public Mono<ScoreAdjustment> submitTeamAdjustment(
      @ValidTeamId final String teamId, final long amount, final String description) {
    final ScoreAdjustmentBuilder scoreAdjustmentBuilder = ScoreAdjustment.builder();

    scoreAdjustmentBuilder.teamId(teamId);
    scoreAdjustmentBuilder.amount(amount);
    scoreAdjustmentBuilder.description(description);
    scoreAdjustmentBuilder.time(LocalDateTime.now(clock));

    return userService
        .getTeamById(teamId)
        .map(TeamEntity::getMembers)
        .map(
            members ->
                members.stream()
                    .map(UserEntity::getId)
                    .collect(Collectors.toCollection(ArrayList::new)))
        .map(scoreAdjustmentBuilder::userIds)
        .map(builder -> builder.build())
        .flatMap(scoreAdjustmentRepository::save);
  }
}
