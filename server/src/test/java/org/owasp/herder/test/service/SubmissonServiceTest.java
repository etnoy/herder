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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService unit tests")
class SubmissonServiceTest {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private SubmissionService submissionService;

  @Mock private SubmissionRepository submissionRepository;

  @Mock private CorrectionRepository correctionRepository;

  @Mock private FlagHandler flagHandler;

  @Test
  void findAllByModuleName_NoSubmissionsExist_ReturnsEmpty() {
    final String mockModuleName = "id";
    when(submissionRepository.findAllByModuleId(mockModuleName)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllByModuleName(mockModuleName)).verifyComplete();

    verify(submissionRepository, times(1)).findAllByModuleId(mockModuleName);
  }

  @Test
  void findAllByModuleName_SubmissionsExist_ReturnsSubmissions() {
    final String mockModuleName = "id";
    final Submission mockSubmission1 = mock(Submission.class);
    final Submission mockSubmission2 = mock(Submission.class);
    final Submission mockSubmission3 = mock(Submission.class);
    final Submission mockSubmission4 = mock(Submission.class);

    when(submissionRepository.findAllByModuleId(mockModuleName))
        .thenReturn(Flux.just(mockSubmission1, mockSubmission2, mockSubmission3, mockSubmission4));
    StepVerifier.create(submissionService.findAllByModuleName(mockModuleName))
        .expectNext(mockSubmission1)
        .expectNext(mockSubmission2)
        .expectNext(mockSubmission3)
        .expectNext(mockSubmission4)
        .verifyComplete();

    verify(submissionRepository, times(1)).findAllByModuleId(mockModuleName);
  }

  @Test
  void findAllRankedByUserId_EmptyUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(submissionService.findAllRankedByUserId(""))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void findAllRankedByUserId_NoRankedSubmissionsExist_ReturnsEmpty() {
    final String mockUserId = "id";
    when(submissionRepository.findAllRankedByUserId(mockUserId)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllRankedByUserId(mockUserId)).verifyComplete();

    verify(submissionRepository, times(1)).findAllRankedByUserId(mockUserId);
  }

  @Test
  void findAllRankedByUserId_RankedSubmissionsExist_ReturnsRankedSubmissions() {
    final String mockUserId = "id";
    final RankedSubmission mockRankedSubmission1 = mock(RankedSubmission.class);
    final RankedSubmission mockRankedSubmission2 = mock(RankedSubmission.class);
    final RankedSubmission mockRankedSubmission3 = mock(RankedSubmission.class);
    final RankedSubmission mockRankedSubmission4 = mock(RankedSubmission.class);

    when(submissionRepository.findAllRankedByUserId(mockUserId))
        .thenReturn(
            Flux.just(
                mockRankedSubmission1,
                mockRankedSubmission2,
                mockRankedSubmission3,
                mockRankedSubmission4));
    StepVerifier.create(submissionService.findAllRankedByUserId(mockUserId))
        .expectNext(mockRankedSubmission1)
        .expectNext(mockRankedSubmission2)
        .expectNext(mockRankedSubmission3)
        .expectNext(mockRankedSubmission4)
        .verifyComplete();
  }

  @Test
  void findAllValidByUserId_EmptyUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(submissionService.findAllValidByUserId(""))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void findAllValidByUserId_NoSubmissionsExist_ReturnsEmpty() {
    final String mockUserId = "id";
    when(submissionRepository.findAllValidByUserId(mockUserId)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllValidByUserId(mockUserId)).verifyComplete();

    verify(submissionRepository, times(1)).findAllValidByUserId(mockUserId);
  }

  @Test
  void findAllValidByUserId_SubmissionsExist_ReturnsSubmissions() {
    final String mockUserId = "id";
    final Submission mockSubmission1 = mock(Submission.class);
    final Submission mockSubmission2 = mock(Submission.class);
    final Submission mockSubmission3 = mock(Submission.class);
    final Submission mockSubmission4 = mock(Submission.class);

    when(submissionRepository.findAllValidByUserId(mockUserId))
        .thenReturn(Flux.just(mockSubmission1, mockSubmission2, mockSubmission3, mockSubmission4));
    StepVerifier.create(submissionService.findAllValidByUserId(mockUserId))
        .expectNext(mockSubmission1)
        .expectNext(mockSubmission2)
        .expectNext(mockSubmission3)
        .expectNext(mockSubmission4)
        .verifyComplete();

    verify(submissionRepository, times(1)).findAllValidByUserId(mockUserId);
  }

  @Test
  void findAllValidByUserIdAndModuleName_InvalidUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(submissionService.findAllValidByUserIdAndModuleName("", "id"))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void findAllValidByUserIdAndModuleName_NoSubmissionsExist_ReturnsEmpty() {
    final String mockUserId = "id";
    final String mockModuleName = "id";
    when(submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName))
        .thenReturn(Mono.empty());
    StepVerifier.create(
            submissionService.findAllValidByUserIdAndModuleName(mockUserId, mockModuleName))
        .verifyComplete();

    verify(submissionRepository, times(1))
        .findAllByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName);
  }

  @Test
  void findAllValidByUserIdAndModuleName_SubmissionsExist_ReturnsSubmissions() {
    final String mockUserId = "id";
    final String mockModuleName = "id";
    final Submission mockSubmission = mock(Submission.class);

    when(submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName))
        .thenReturn(Mono.just(mockSubmission));
    StepVerifier.create(
            submissionService.findAllValidByUserIdAndModuleName(mockUserId, mockModuleName))
        .expectNext(mockSubmission)
        .verifyComplete();

    verify(submissionRepository, times(1))
        .findAllByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName);
  }

  private void setClock(final Clock clock) {
    submissionService.setClock(clock);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    submissionService = new SubmissionService(submissionRepository, flagHandler);
  }

  @Test
  void submit_InvalidFlag_ReturnsInvalidSubmission() {
    final String mockUserId = "userId";
    final String mockModuleId = "id";
    final String mockSubmissionId = "submissionId";

    final String flag = "invalidFlag";

    final Clock fixedClock =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

    setClock(fixedClock);

    when(flagHandler.verifyFlag(mockUserId, mockModuleId, flag)).thenReturn(Mono.just(false));

    when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId))
        .thenReturn(Mono.just(false));

    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            user -> Mono.just(user.getArgument(0, Submission.class).withId(mockSubmissionId)));

    StepVerifier.create(submissionService.submit(mockUserId, mockModuleId, flag))
        .assertNext(
            submission -> {
              assertThat(submission.getId()).isEqualTo(mockSubmissionId);
              assertThat(submission.getUserId()).isEqualTo(mockUserId);
              assertThat(submission.getModuleId()).isEqualTo(mockModuleId);
              assertThat(submission.getFlag()).isEqualTo(flag);
              assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(fixedClock));
              assertThat(submission.isValid()).isFalse();
            })
        .verifyComplete();

    verify(flagHandler, times(1)).verifyFlag(mockUserId, mockModuleId, flag);
    verify(submissionRepository, times(1))
        .existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId);
    verify(submissionRepository, times(1)).save(any(Submission.class));
  }

  @Test
  void submit_InvalidUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(submissionService.submit("", "module", "flag"))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void submit_ModuleAlreadySolvedByUser_ReturnsModuleAlreadySolvedException() {
    final String mockUserId = "id";
    final String mockModuleName = "id";
    final String flag = "validFlag";

    final Clock fixedClock =
        Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

    setClock(fixedClock);

    when(flagHandler.verifyFlag(mockUserId, mockModuleName, flag)).thenReturn(Mono.just(true));

    when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName))
        .thenReturn(Mono.just(true));

    StepVerifier.create(submissionService.submit(mockUserId, mockModuleName, flag))
        .expectError(ModuleAlreadySolvedException.class)
        .verify();

    verify(flagHandler, times(1)).verifyFlag(mockUserId, mockModuleName, flag);
    verify(submissionRepository, times(1))
        .existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName);
  }

  @Test
  void submit_ValidFlag_ReturnsValidSubmission() {
    final String mockUserId = "user-id";
    final String mockModuleId = "module-name";
    final String mockSubmissionId = "sub-id";

    final String flag = "validFlag";

    final Clock fixedClock = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

    setClock(fixedClock);

    when(flagHandler.verifyFlag(mockUserId, mockModuleId, flag)).thenReturn(Mono.just(true));

    when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId))
        .thenReturn(Mono.just(false));

    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            user -> Mono.just(user.getArgument(0, Submission.class).withId(mockSubmissionId)));

    StepVerifier.create(submissionService.submit(mockUserId, mockModuleId, flag))
        .assertNext(
            submission -> {
              assertThat(submission.getId()).isEqualTo(mockSubmissionId);
              assertThat(submission.getUserId()).isEqualTo(mockUserId);
              assertThat(submission.getModuleId()).isEqualTo(mockModuleId);
              assertThat(submission.getFlag()).isEqualTo(flag);
              assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(fixedClock));
              assertThat(submission.isValid()).isTrue();
            })
        .verifyComplete();

    verify(flagHandler, times(1)).verifyFlag(mockUserId, mockModuleId, flag);
    verify(submissionRepository, times(1))
        .existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId);
    verify(submissionRepository, times(1)).save(any(Submission.class));
  }

  @Test
  void submitValid_EmptyUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(submissionService.submitValid("", "id"))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void submitValid_ModuleAlreadySolvedByUser_ReturnsModuleAlreadySolvedException() {
    final String mockUserId = "id";
    final String mockModuleName = "id";

    final Clock fixedClock = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

    setClock(fixedClock);

    when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName))
        .thenReturn(Mono.just(true));

    StepVerifier.create(submissionService.submitValid(mockUserId, mockModuleName))
        .expectError(ModuleAlreadySolvedException.class)
        .verify();

    verify(submissionRepository, times(1))
        .existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleName);
  }

  @Test
  void submitValid_ModuleNotAlreadySolved_ReturnsValidSubmission() {
    final String mockUserId = "id";
    final String mockModuleId = "id";
    final String mockSubmissionId = "submissionId";

    final Clock fixedClock = Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

    setClock(fixedClock);

    when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId))
        .thenReturn(Mono.just(false));

    when(submissionRepository.save(any(Submission.class)))
        .thenAnswer(
            user -> Mono.just(user.getArgument(0, Submission.class).withId(mockSubmissionId)));

    StepVerifier.create(submissionService.submitValid(mockUserId, mockModuleId))
        .assertNext(
            submission -> {
              assertThat(submission.getId()).isEqualTo(mockSubmissionId);
              assertThat(submission.getUserId()).isEqualTo(mockUserId);
              assertThat(submission.getModuleId()).isEqualTo(mockModuleId);
              assertThat(submission.getFlag()).isNull();
              assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(fixedClock));
              assertThat(submission.isValid()).isTrue();
            })
        .verifyComplete();

    verify(submissionRepository, times(1))
        .existsByUserIdAndModuleIdAndIsValidTrue(mockUserId, mockModuleId);
    verify(submissionRepository, times(1)).save(any(Submission.class));
  }
}
