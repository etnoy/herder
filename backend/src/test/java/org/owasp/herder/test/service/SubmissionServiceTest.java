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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class SubmissionServiceTest extends BaseTest {

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
  void findAllRankedByUserId_RankedSubmissionExist_ReturnsSanitizedRankedSubmission() {
    when(rankedSubmissionRepository.findAllByUserId(TestConstants.TEST_USER_ID))
      .thenReturn(Flux.just(TestConstants.TEST_RANKED_SUBMISSION));

    StepVerifier
      .create(submissionService.findAllRankedByUserId(TestConstants.TEST_USER_ID))
      .expectNext(TestConstants.TEST_SANITIZED_RANKED_SUBMISSION)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find ranked submissions by team id")
  void findAllRankedByTeamId_RankedSubmissionExist_ReturnsSanitizedRankedSubmission() {
    when(rankedSubmissionRepository.findAllByTeamId(TestConstants.TEST_TEAM_ID))
      .thenReturn(Flux.just(TestConstants.TEST_RANKED_SUBMISSION));

    StepVerifier
      .create(submissionService.findAllRankedByTeamId(TestConstants.TEST_TEAM_ID))
      .expectNext(TestConstants.TEST_SANITIZED_RANKED_SUBMISSION)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find zero valid submissions by user id")
  void findAllValidByUserId_NoSubmissionsExist_ReturnsEmpty() {
    when(submissionRepository.findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID)).thenReturn(Flux.empty());
    StepVerifier.create(submissionService.findAllValidByUserId(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can find valid submissions by user id")
  void findAllSubmissions_SubmissionsExist_ReturnsSubmissions() {
    final Submission mockSubmission1 = mock(Submission.class);
    final Submission mockSubmission2 = mock(Submission.class);
    final Submission mockSubmission3 = mock(Submission.class);
    final Submission mockSubmission4 = mock(Submission.class);

    when(submissionRepository.findAll())
      .thenReturn(Flux.just(mockSubmission1, mockSubmission2, mockSubmission3, mockSubmission4));
    StepVerifier
      .create(submissionService.findAllSubmissions())
      .expectNext(mockSubmission1)
      .expectNext(mockSubmission2)
      .expectNext(mockSubmission3)
      .expectNext(mockSubmission4)
      .verifyComplete();
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
  }

  @Test
  @DisplayName("Can find valid submissions by user id and module name")
  void refreshSubmissionRanks_SubmissionsExist_ReturnsNothing() {
    when(submissionRepository.refreshSubmissionRanks()).thenReturn(Flux.empty());

    StepVerifier.create(submissionService.refreshSubmissionRanks()).verifyComplete();
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
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

    setClock(TestConstants.YEAR_2000_CLOCK);

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
        assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK));
        assertThat(submission.isValid()).isFalse();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error when submitting a valid flag to an already solved module")
  void submit_ModuleAlreadySolvedByUser_ReturnsModuleAlreadySolvedException() {
    final UserEntity mockUser = mock(UserEntity.class);
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    setClock(TestConstants.YEAR_2000_CLOCK);

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
  }

  @Test
  @DisplayName("Can persist a submission entity when a flag is submitted")
  void submit_ValidFlag_ReturnsValidSubmission() {
    final String mockSubmissionId = "sub-id";

    setClock(TestConstants.YEAR_2000_CLOCK);

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
        assertThat(submission.getTime()).isEqualTo(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK));
        assertThat(submission.isValid()).isTrue();
      })
      .verifyComplete();
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
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();

    when(rankedSubmissionRepository.findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Flux.just(rankedSubmission));

    final SanitizedRankedSubmission sanitizedRankedSubmission = SanitizedRankedSubmission
      .builder()
      .id(TestConstants.TEST_USER_ID)
      .solverType(SolverType.USER)
      .moduleName(TestConstants.TEST_MODULE_NAME)
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .moduleLocator(TestConstants.TEST_MODULE_LOCATOR)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
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
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();

    when(rankedSubmissionRepository.findAllByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Flux.just(rankedSubmission));

    final SanitizedRankedSubmission sanitizedRankedSubmission = SanitizedRankedSubmission
      .builder()
      .id(TestConstants.TEST_TEAM_ID)
      .solverType(SolverType.TEAM)
      .moduleName(TestConstants.TEST_MODULE_NAME)
      .displayName(TestConstants.TEST_TEAM_DISPLAY_NAME)
      .moduleLocator(TestConstants.TEST_MODULE_LOCATOR)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();

    StepVerifier
      .create(submissionService.findAllRankedByModuleLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(sanitizedRankedSubmission)
      .verifyComplete();
  }

  @Test
  @DisplayName(
    "Can error when finding ranked submissions by module locator and the submission has neither user or team"
  )
  void findAllRankedByModuleLocator_RankedSubmissionWithoutUserOrTeam_Errors() {
    final RankedSubmission rankedSubmission = RankedSubmission
      .builder()
      .module(TestConstants.TEST_MODULE_ENTITY)
      .rank(1L)
      .baseScore(100L)
      .bonusScore(10L)
      .score(110L)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
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

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("Can set team id of user submissions")
  void setTeamIdOfUserSubmissions_SubmissionToModify_SavesModifiedSubmissions() {
    final Submission testSubmission = Submission
      .builder()
      .userId(TestConstants.TEST_USER_ID)
      .moduleId(TestConstants.TEST_MODULE_ID)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();
    when(submissionRepository.findAllByUserId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(testSubmission));

    when(submissionRepository.saveAll(any(Flux.class))).thenReturn(Flux.empty());

    StepVerifier
      .create(submissionService.setTeamIdOfUserSubmissions(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .verifyComplete();

    ArgumentCaptor<Flux<Submission>> fluxCaptor = ArgumentCaptor.forClass(Flux.class);
    verify(submissionRepository).saveAll(fluxCaptor.capture());
    StepVerifier
      .create(fluxCaptor.getValue())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(members -> {
        assertThat(members).extracting("teamId").containsOnly(TestConstants.TEST_TEAM_ID);
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("Can error when setting team id of submissions with team id already set")
  void setTeamIdOfUserSubmissions_TeamIdAlreadySet_Errors() {
    final Submission testSubmission = Submission
      .builder()
      .userId(TestConstants.TEST_USER_ID)
      .teamId(TestConstants.TEST_TEAM_ID)
      .moduleId(TestConstants.TEST_MODULE_ID)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();
    when(submissionRepository.findAllByUserId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(testSubmission));

    when(submissionRepository.saveAll(any(Flux.class))).thenAnswer(i -> i.getArguments()[0]);

    StepVerifier
      .create(submissionService.setTeamIdOfUserSubmissions(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .expectErrorMatches(e ->
        e instanceof IllegalStateException &&
        e
          .getMessage()
          .equals(
            String.format(
              "Ranked submission for user \"%s\" and module \"%s\" already has team id set",
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_MODULE_ID
            )
          )
      )
      .verify();
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("Can clear team id of user submissions")
  void clearTeamIdOfUserSubmissions_SubmissionToModify_SavesModifiedSubmissions() {
    final Submission testSubmission = Submission
      .builder()
      .userId(TestConstants.TEST_USER_ID)
      .teamId(TestConstants.TEST_TEAM_ID)
      .moduleId(TestConstants.TEST_MODULE_ID)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();
    when(submissionRepository.findAllByUserId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(testSubmission));

    when(submissionRepository.saveAll(any(Flux.class))).thenReturn(Flux.empty());

    StepVerifier.create(submissionService.clearTeamIdOfUserSubmissions(TestConstants.TEST_USER_ID)).verifyComplete();

    ArgumentCaptor<Flux<Submission>> fluxCaptor = ArgumentCaptor.forClass(Flux.class);
    verify(submissionRepository).saveAll(fluxCaptor.capture());
    StepVerifier
      .create(fluxCaptor.getValue())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(members -> {
        assertThat(members).extracting("teamId").containsOnlyNulls();
      })
      .verifyComplete();
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("Can clear team id of user submissions with no team id set")
  void clearTeamIdOfUserSubmissions_NoTeamIdSet_SavesModifiedSubmissions() {
    final Submission testSubmission = Submission
      .builder()
      .userId(TestConstants.TEST_USER_ID)
      .moduleId(TestConstants.TEST_MODULE_ID)
      .time(LocalDateTime.now(TestConstants.YEAR_2000_CLOCK))
      .build();
    when(submissionRepository.findAllByUserId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(testSubmission));

    when(submissionRepository.saveAll(any(Flux.class))).thenReturn(Flux.empty());

    StepVerifier.create(submissionService.clearTeamIdOfUserSubmissions(TestConstants.TEST_USER_ID)).verifyComplete();

    ArgumentCaptor<Flux<Submission>> fluxCaptor = ArgumentCaptor.forClass(Flux.class);
    verify(submissionRepository).saveAll(fluxCaptor.capture());
    StepVerifier
      .create(fluxCaptor.getValue())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(members -> {
        assertThat(members).extracting("teamId").containsOnlyNulls();
      })
      .verifyComplete();
  }
}
