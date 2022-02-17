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
package org.owasp.herder.it.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.CorrectionService;
import org.owasp.herder.scoring.ModulePointRepository;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.ScoreboardController;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import io.github.bucket4j.Bucket;
import reactor.core.publisher.Hooks;
import reactor.test.StepVerifier;

@DisplayName("SubmissionRepository integration tests")
class SubmissionRepositoryIT extends BaseIT {
  @Autowired ModuleService moduleService;

  @Autowired UserService userService;

  @Autowired SubmissionService submissionService;

  @Autowired CorrectionService correctionService;

  @Autowired ScoreService scoreService;

  @Autowired ScoreboardController scoreboardController;

  @Autowired CorrectionRepository correctionRepository;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired ModulePointRepository modulePointRepository;

  @Autowired FlagHandler flagHandler;

  @Autowired ConfigurationService configurationService;

  @Autowired KeyService keyService;

  @Autowired CryptoService cryptoService;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @MockBean FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Test
  @DisplayName("Can get ranked submissions")
  void canGetRankedSubmissions() {
    // We'll use this exact flag
    final String flag = "itsaflag";

    // And this will be an incorrect flag
    final String wrongFlag = "itsanincorrectflag";

    // Create six users and store their ids
    List<String> displayNames = new ArrayList<>();
    displayNames.add("TestUser1");
    displayNames.add("TestUser2");
    displayNames.add("TestUser3");
    displayNames.add("TestUser4");
    displayNames.add("TestUser5");
    displayNames.add("TestUser6");
    displayNames.add("TestUser7");
    displayNames.add("TestUser8");

    Iterator<String> displayNameIterator = displayNames.iterator();

    List<String> userIds = new ArrayList<>();
    while (displayNameIterator.hasNext()) {
      userIds.add(userService.create(displayNameIterator.next()).block());
    }

    // Create a module to submit to
    final String moduleId1 = moduleService.create("Test module", "id1").block();

    // Set that module to have an exact flag
    moduleService.setStaticFlag(moduleId1, flag).block();

    // Set scoring levels for module1
    scoreService.setModuleScore(moduleId1, 0, 100).block();

    scoreService.setModuleScore(moduleId1, 1, 50).block();
    scoreService.setModuleScore(moduleId1, 2, 40).block();
    scoreService.setModuleScore(moduleId1, 3, 30).block();
    scoreService.setModuleScore(moduleId1, 4, 20).block();

    // Create some other modules we aren't interested in
    final String moduleId2 = moduleService.create("Test module 2", "id2").block();
    moduleService.setStaticFlag(moduleId2, flag).block();

    // Set scoring levels for module2
    scoreService.setModuleScore(moduleId2, 0, 50).block();
    scoreService.setModuleScore(moduleId2, 1, 30).block();
    scoreService.setModuleScore(moduleId2, 2, 10).block();

    final String moduleId3 = moduleService.create("Test module 3", "id3").block();

    moduleService.setStaticFlag(moduleId3, flag).block();

    // You only get 1 point for this module
    scoreService.setModuleScore(moduleId3, 0, 1).block();

    // Create a fixed clock from which we will base our offset submission times
    final Clock startTime =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

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
    Iterator<String> userIdIterator = userIds.iterator();
    Iterator<Clock> clockIterator = clocks.iterator();
    Iterator<String> flagIterator = flags.iterator();

    while (userIdIterator.hasNext() && clockIterator.hasNext() && flagIterator.hasNext()) {
      // Recreate the submission service every time with a new clock
      submissionService.setClock(clockIterator.next());

      final String currentUserId = userIdIterator.next();
      final String currentFlag = flagIterator.next();

      // Submit a new flag
      submissionService.submit(currentUserId, moduleId1, currentFlag).block();
      submissionService.submit(currentUserId, moduleId2, currentFlag).block();
      submissionService.submit(currentUserId, moduleId3, currentFlag).block();
    }

    // Now we verify

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(0)))
        .expectNextMatches(submission -> submission.getRank() == 4)
        .expectNextMatches(submission -> submission.getRank() == 4)
        .expectNextMatches(submission -> submission.getRank() == 4)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(1)))
        .expectNextMatches(submission -> submission.getRank() == 6)
        .expectNextMatches(submission -> submission.getRank() == 6)
        .expectNextMatches(submission -> submission.getRank() == 6)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(2)))
        .expectNextMatches(submission -> submission.getRank() == 2)
        .expectNextMatches(submission -> submission.getRank() == 2)
        .expectNextMatches(submission -> submission.getRank() == 2)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(3)))
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(4)))
        .expectNextMatches(submission -> submission.getRank() == 4)
        .expectNextMatches(submission -> submission.getRank() == 4)
        .expectNextMatches(submission -> submission.getRank() == 4)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(5)))
        .expectNextMatches(submission -> submission.getRank() == 2)
        .expectNextMatches(submission -> submission.getRank() == 2)
        .expectNextMatches(submission -> submission.getRank() == 2)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(6)))
        .expectNextMatches(submission -> submission.getRank() == 1)
        .expectNextMatches(submission -> submission.getRank() == 1)
        .expectNextMatches(submission -> submission.getRank() == 1)
        .verifyComplete();

    StepVerifier.create(submissionRepository.findAllRankedByUserId(userIds.get(7)))
        .verifyComplete();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
  }

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }
}
