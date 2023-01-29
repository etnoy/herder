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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.RankedSubmission;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.SanitizedRankedSubmission;
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionService unit tests")
class SubmissonServiceTest extends BaseTest {

  private SubmissionService submissionService;

  @Mock
  SubmissionRepository submissionRepository;

  @Mock
  RankedSubmissionRepository rankedSubmissionRepository;

  @Mock
  FlagHandler flagHandler;

  @Mock
  UserService userService;

  @Mock
  ModuleService moduleService;

  @Mock
  Clock clock;

  UserEntity mockUser;

  ModuleEntity mockModule;

  TeamEntity mockTeam;

  @Test
  @DisplayName("Can find ranked submissions by user id")
  void findAllRankedByUserId_NoRankedSubmissionsExist_ReturnsEmpty() {
    when(rankedSubmissionRepository.findAllByUserId(TestConstants.TEST_USER_ID)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllRankedByUserId(TestConstants.TEST_USER_ID)).verifyComplete();

    verify(rankedSubmissionRepository, times(1)).findAllByUserId(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can find zero valid submissions by user id")
  void findAllValidByUserId_NoSubmissionsExist_ReturnsEmpty() {
    when(submissionRepository.findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllValidByUserId(TestConstants.TEST_USER_ID)).verifyComplete();

    verify(submissionRepository, times(1)).findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can find valid submissions by user id")
  void findAllValidByUserId_SubmissionsExist_ReturnsSubmissions() {
    final Submission mockSubmission1 = mock(Submission.class);
    final Submission mockSubmission2 = mock(Submission.class);
    final Submission mockSubmission3 = mock(Submission.class);
    final Submission mockSubmission4 = mock(Submission.class);

    when(submissionRepository.findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID))
      .thenReturn(Flux.just(mockSubmission1, mockSubmission2, mockSubmission3, mockSubmission4));
    StepVerifier
      .create(submissionService.findAllValidByUserId(TestConstants.TEST_USER_ID))
      .expectNext(mockSubmission1)
      .expectNext(mockSubmission2)
      .expectNext(mockSubmission3)
      .expectNext(mockSubmission4)
      .verifyComplete();

    verify(submissionRepository, times(1)).findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can find zero valid submissions by user id and module name")
  void findAllValidByUserIdAndModuleName_NoSubmissionsExist_ReturnsEmpty() {
    when(
      submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_NAME
      )
    )
      .thenReturn(Mono.empty());
    StepVerifier
      .create(
        submissionService.findAllValidByUserIdAndModuleName(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_NAME)
      )
      .verifyComplete();

    verify(submissionRepository, times(1))
      .findAllByUserIdAndModuleIdAndIsValidTrue(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_NAME);
  }

  @Test
  @DisplayName("Can find valid submissions by user id and module name")
  void findAllValidByUserIdAndModuleName_SubmissionsExist_ReturnsSubmissions() {
    final Submission mockSubmission = mock(Submission.class);

    when(
      submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_NAME
      )
    )
      .thenReturn(Mono.just(mockSubmission));
    StepVerifier
      .create(
        submissionService.findAllValidByUserIdAndModuleName(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_NAME)
      )
      .expectNext(mockSubmission)
      .verifyComplete();

    verify(submissionRepository, times(1))
      .findAllByUserIdAndModuleIdAndIsValidTrue(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_NAME);
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    submissionService =
      new SubmissionService(
        submissionRepository,
        rankedSubmissionRepository,
        flagHandler,
        userService,
        moduleService,
        clock
      );

    mockUser = mock(UserEntity.class);
    mockModule = mock(ModuleEntity.class);
    mockTeam = mock(TeamEntity.class);
  }

  @Test
  @DisplayName("Can persist an invalid submission entity when an invalid flag is submitted")
  void submit_InvalidFlag_ReturnsInvalidSubmission() {
    final String mockSubmissionId = "submissionId";

    setClock(TestConstants.year2000Clock);

    when(
      flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG)
    )
      .thenReturn(Mono.just(false));

    when(
      submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(false));

    when(submissionRepository.save(any(Submission.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, Submission.class).withId(mockSubmissionId)));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(mockModule.isOpen()).thenReturn(true);

    StepVerifier
      .create(
        submissionService.submitFlag(
          TestConstants.TEST_USER_ID,
          TestConstants.TEST_MODULE_ID,
          TestConstants.TEST_STATIC_FLAG
        )
      )
      .assertNext(submission -> {
        assertThat(submission.getId()).isEqualTo(mockSubmissionId);
        assertThat(submission.getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
        assertThat(submission.getModuleId()).isEqualTo(TestConstants.TEST_MODULE_ID);
        assertThat(submission.getFlag()).isEqualTo(TestConstants.TEST_STATIC_FLAG);
        assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
        assertThat(submission.isValid()).isFalse();
      })
      .verifyComplete();

    verify(flagHandler, times(1))
      .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG);
    verify(submissionRepository, times(1))
      .existsByUserIdAndModuleIdAndIsValidTrue(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
    verify(submissionRepository, times(1)).save(any(Submission.class));
  }

  @Test
  @DisplayName("Can error when submitting a valid flag to an already solved module")
  void submit_ModuleAlreadySolvedByUser_ReturnsModuleAlreadySolvedException() {
    final UserEntity mockUser = mock(UserEntity.class);
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    setClock(TestConstants.year2000Clock);

    when(
      flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG)
    )
      .thenReturn(Mono.just(true));

    when(
      submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(true));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    StepVerifier
      .create(
        submissionService.submitFlag(
          TestConstants.TEST_USER_ID,
          TestConstants.TEST_MODULE_ID,
          TestConstants.TEST_STATIC_FLAG
        )
      )
      .expectError(ModuleAlreadySolvedException.class)
      .verify();

    verify(flagHandler, times(1))
      .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG);
    verify(submissionRepository, times(1))
      .existsByUserIdAndModuleIdAndIsValidTrue(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
  }

  @Test
  @DisplayName("Can persist a submission entity when a flag is submitted")
  void submit_ValidFlag_ReturnsValidSubmission() {
    final String mockSubmissionId = "sub-id";

    setClock(TestConstants.year2000Clock);

    when(
      flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG)
    )
      .thenReturn(Mono.just(true));

    when(
      submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_MODULE_ID
      )
    )
      .thenReturn(Mono.just(false));

    when(submissionRepository.save(any(Submission.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, Submission.class).withId(mockSubmissionId)));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(mockModule.isOpen()).thenReturn(true);

    StepVerifier
      .create(
        submissionService.submitFlag(
          TestConstants.TEST_USER_ID,
          TestConstants.TEST_MODULE_ID,
          TestConstants.TEST_STATIC_FLAG
        )
      )
      .assertNext(submission -> {
        assertThat(submission.getId()).isEqualTo(mockSubmissionId);
        assertThat(submission.getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
        assertThat(submission.getModuleId()).isEqualTo(TestConstants.TEST_MODULE_ID);
        assertThat(submission.getFlag()).isEqualTo(TestConstants.TEST_STATIC_FLAG);
        assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
        assertThat(submission.isValid()).isTrue();
      })
      .verifyComplete();

    verify(flagHandler, times(1))
      .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG);
    verify(submissionRepository, times(1))
      .existsByUserIdAndModuleIdAndIsValidTrue(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
    verify(submissionRepository, times(1)).save(any(Submission.class));
  }

  @Test
  @DisplayName("Can find ranked submissions with user id by module locator")
  void findAllRankedByModuleLocator_SubmissionsExistWithUser_ReturnsSubmissions() {
    final RankedSubmission rankedSubmission = RankedSubmission
      .builder()
      .module(TestConstants.TEST_MODULE_ENTITY)
      .user(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID))
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.year2000Clock))
      .build();

    when(rankedSubmissionRepository.findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Flux.just(rankedSubmission));

    final SanitizedRankedSubmission sanitizedRankedSubmission = SanitizedRankedSubmission
      .builder()
      .id(TestConstants.TEST_USER_ID)
      .principalType(SolverType.USER)
      .moduleName(TestConstants.TEST_MODULE_NAME)
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .moduleLocator(TestConstants.TEST_MODULE_LOCATOR)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.year2000Clock))
      .build();

    StepVerifier
      .create(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(sanitizedRankedSubmission)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find ranked submissions with team id by module locator")
  void findAllRankedByModuleLocator_SubmissionsExistWithTeam_ReturnsSubmissions() {
    final RankedSubmission rankedSubmission = RankedSubmission
      .builder()
      .module(TestConstants.TEST_MODULE_ENTITY)
      .team(TestConstants.TEST_TEAM_ENTITY.withId(TestConstants.TEST_TEAM_ID))
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.year2000Clock))
      .build();

    when(rankedSubmissionRepository.findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Flux.just(rankedSubmission));

    final SanitizedRankedSubmission sanitizedRankedSubmission = SanitizedRankedSubmission
      .builder()
      .id(TestConstants.TEST_TEAM_ID)
      .principalType(SolverType.TEAM)
      .moduleName(TestConstants.TEST_MODULE_NAME)
      .displayName(TestConstants.TEST_TEAM_DISPLAY_NAME)
      .moduleLocator(TestConstants.TEST_MODULE_LOCATOR)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.year2000Clock))
      .build();

    StepVerifier
      .create(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(sanitizedRankedSubmission)
      .verifyComplete();
  }

  @Test
  @DisplayName("Error when finding ranked submissions with neither user or team by module locator")
  void findAllRankedByModuleLocator_RankedSubmissionWithoutUserOrTeam_Errors() {
    final RankedSubmission rankedSubmission = RankedSubmission
      .builder()
      .module(TestConstants.TEST_MODULE_ENTITY)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.year2000Clock))
      .build();

    when(rankedSubmissionRepository.findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Flux.just(rankedSubmission));

    StepVerifier
      .create(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectErrorMatches(e ->
        e instanceof IllegalArgumentException &&
        e.getMessage().equals(String.format("Ranked submission is missing user or team", TestConstants.TEST_USER_ID))
      )
      .verify();
  }
}
