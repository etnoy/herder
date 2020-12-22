/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.it.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModulePointRepository;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.CorrectionService;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("ScoringService integration test")
class ScoringServiceIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired ModuleService moduleService;

  @Autowired UserService userService;

  @Autowired SubmissionService submissionService;

  @Autowired CorrectionService correctionService;

  @Autowired ScoreService scoringService;

  @Autowired CorrectionRepository correctionRepository;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired ModulePointRepository modulePointRepository;

  @Autowired FlagHandler flagComponent;

  @Autowired Clock clock;

  @Autowired TestUtils testService;

  @Autowired ConfigurationService configurationService;

  @Autowired KeyService keyService;

  @Autowired CryptoService cryptoService;

  @Test
  void computeScoreForModule_SubmittedScores_ReturnsCorrectScoresForUsers() throws Exception {
    // We'll use this exact flag
    final String flag = "itsaflag";

    // And this will be an incorrect flag
    final String wrongFlag = "itsanincorrectflag";

    // Create six users and store their ids
    List<Long> userIds = new ArrayList<>();
    userIds.add(userService.create("TestUser1").block());
    userIds.add(userService.create("TestUser2").block());
    userIds.add(userService.create("TestUser3").block());
    userIds.add(userService.create("TestUser4").block());
    userIds.add(userService.create("TestUser5").block());
    userIds.add(userService.create("TestUser6").block());
    userIds.add(userService.create("TestUser7").block());
    userIds.add(userService.create("TestUser8").block());

    // Create a module to submit to
    moduleService.create("id1").block().getId();

    // Set that module to have an exact flag
    moduleService.setStaticFlag("id1", flag).block();

    // Set scoring levels for module1
    scoringService.setModuleScore("id1", 0, 100).block();

    scoringService.setModuleScore("id1", 1, 50).block();
    scoringService.setModuleScore("id1", 2, 40).block();
    scoringService.setModuleScore("id1", 3, 30).block();
    scoringService.setModuleScore("id1", 4, 20).block();

    // Create some other modules we aren't interested in
    moduleService.create("id2").block();
    moduleService.setStaticFlag("id2", flag).block();

    // Set scoring levels for module2
    scoringService.setModuleScore("id2", 0, 50).block();
    scoringService.setModuleScore("id2", 1, 30).block();
    scoringService.setModuleScore("id2", 2, 10).block();

    moduleService.create("id3").block();

    moduleService.setStaticFlag("id3", flag).block();

    // You only get 1 point for this module
    scoringService.setModuleScore("id3", 0, 1).block();

    // Create a fixed clock from which we will base our offset submission times
    final Clock startTime = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

    // Create a list of times at which the above six users will submit their solutions
    List<Integer> timeOffsets = Arrays.asList(3, 4, 1, 2, 3, 1, 0, 5);

    // The duration between times should be 1 day
    final List<Clock> clocks =
        timeOffsets.stream()
            .map(Duration::ofDays)
            .map(duration -> Clock.offset(startTime, duration))
            .collect(Collectors.toList());

    final List<String> flags =
        Arrays.asList(flag, flag, flag, wrongFlag, flag, flag, flag, wrongFlag);

    // Iterate over the user ids and clocks at the same time
    Iterator<Long> userIdIterator = userIds.iterator();
    Iterator<Clock> clockIterator = clocks.iterator();
    Iterator<String> flagIterator = flags.iterator();

    while (userIdIterator.hasNext() && clockIterator.hasNext() && flagIterator.hasNext()) {
      // Recreate the submission service every time with a new clock
      submissionService.setClock(clockIterator.next());

      final Long currentUserId = userIdIterator.next();
      final String currentFlag = flagIterator.next();

      // Submit a new flag
      submissionService.submit(currentUserId, "id1", currentFlag).block();
      submissionService.submit(currentUserId, "id2", currentFlag).block();
      submissionService.submit(currentUserId, "id3", currentFlag).block();
    }

    final Clock correctionClock =
        Clock.fixed(Instant.parse("2000-01-04T10:00:00.00Z"), ZoneId.of("Z"));
    submissionService.setClock(correctionClock);
    correctionService.submit(userIds.get(2), -1000, "Penalty for cheating").block();
    submissionService.setClock(Clock.offset(correctionClock, Duration.ofHours(10)));
    correctionService.submit(userIds.get(1), 100, "Thanks for the bribe").block();

    StepVerifier.create(scoringService.getScoreboard())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(1L)
                .userId(userIds.get(1))
                .score(251L)
                .goldMedals(0L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(2L)
                .userId(userIds.get(6))
                .score(231L)
                .goldMedals(3L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(3L)
                .userId(userIds.get(5))
                .score(201L)
                .goldMedals(0L)
                .silverMedals(3L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(4L)
                .userId(userIds.get(0))
                .score(171L)
                .goldMedals(0L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(4L)
                .userId(userIds.get(4))
                .score(171L)
                .goldMedals(0L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(6L)
                .userId(userIds.get(3))
                .score(0L)
                .goldMedals(0L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(6L)
                .userId(userIds.get(7))
                .score(0L)
                .goldMedals(0L)
                .silverMedals(0L)
                .bronzeMedals(0L)
                .build())
        .expectNext(
            ScoreboardEntry.builder()
                .rank(8L)
                .userId(userIds.get(2))
                .score(-799L)
                .goldMedals(0L)
                .silverMedals(3L)
                .bronzeMedals(0L)
                .build())
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void clear() {
    testService.deleteAll().block();
  }
}
