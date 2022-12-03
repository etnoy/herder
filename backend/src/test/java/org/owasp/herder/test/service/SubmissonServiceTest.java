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
import org.owasp.herder.scoring.RankedSubmissionRepository;
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

    @Mock SubmissionRepository submissionRepository;

    @Mock RankedSubmissionRepository rankedSubmissionRepository;

    @Mock FlagHandler flagHandler;

    @Mock UserService userService;

    @Mock ModuleService moduleService;

    @Mock Clock clock;

    UserEntity mockUser;

    //  @Test
    //  void findAllRankedByUserId_RankedSubmissionsExist_ReturnsRankedSubmissions() {
    //
    //    final RankedSubmission mockRankedSubmission1 = mock(RankedSubmission.class);
    //    final RankedSubmission mockRankedSubmission2 = mock(RankedSubmission.class);
    //    final RankedSubmission mockRankedSubmission3 = mock(RankedSubmission.class);
    //    final RankedSubmission mockRankedSubmission4 = mock(RankedSubmission.class);
    //
    //    when(submissionRepository.findAllRankedByUserId(TestConstants.TEST_USER_ID))
    //        .thenReturn(
    //            Flux.just(
    //                mockRankedSubmission1,
    //                mockRankedSubmission2,
    //                mockRankedSubmission3,
    //                mockRankedSubmission4));
    //
    //    StepVerifier.create(submissionService.findAllRankedByUserId(TestConstants.TEST_USER_ID))
    //        .expectNext(mockRankedSubmission1)
    //        .expectNext(mockRankedSubmission2)
    //        .expectNext(mockRankedSubmission3)
    //        .expectNext(mockRankedSubmission4)
    //        .verifyComplete();
    //  }

    ModuleEntity mockModule;

    TeamEntity mockTeam;

    @Test
    void findAllRankedByUserId_NoRankedSubmissionsExist_ReturnsEmpty() {

        when(rankedSubmissionRepository.findAllByUserId(TestConstants.TEST_USER_ID))
                .thenReturn(Flux.empty());
        StepVerifier.create(submissionService.findAllRankedByUserId(TestConstants.TEST_USER_ID))
                .verifyComplete();

        verify(rankedSubmissionRepository, times(1)).findAllByUserId(TestConstants.TEST_USER_ID);
    }

    @Test
    void findAllValidByUserId_NoSubmissionsExist_ReturnsEmpty() {

        when(submissionRepository.findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID))
                .thenReturn(Flux.empty());
        StepVerifier.create(submissionService.findAllValidByUserId(TestConstants.TEST_USER_ID))
                .verifyComplete();

        verify(submissionRepository, times(1))
                .findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID);
    }

    @Test
    void findAllValidByUserId_SubmissionsExist_ReturnsSubmissions() {

        final Submission mockSubmission1 = mock(Submission.class);
        final Submission mockSubmission2 = mock(Submission.class);
        final Submission mockSubmission3 = mock(Submission.class);
        final Submission mockSubmission4 = mock(Submission.class);

        when(submissionRepository.findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID))
                .thenReturn(
                        Flux.just(
                                mockSubmission1,
                                mockSubmission2,
                                mockSubmission3,
                                mockSubmission4));
        StepVerifier.create(submissionService.findAllValidByUserId(TestConstants.TEST_USER_ID))
                .expectNext(mockSubmission1)
                .expectNext(mockSubmission2)
                .expectNext(mockSubmission3)
                .expectNext(mockSubmission4)
                .verifyComplete();

        verify(submissionRepository, times(1))
                .findAllByUserIdAndIsValidTrue(TestConstants.TEST_USER_ID);
    }

    @Test
    void findAllValidByUserIdAndModuleName_NoSubmissionsExist_ReturnsEmpty() {

        final String mockModuleName = "id";
        when(submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, mockModuleName))
                .thenReturn(Mono.empty());
        StepVerifier.create(
                        submissionService.findAllValidByUserIdAndModuleName(
                                TestConstants.TEST_USER_ID, mockModuleName))
                .verifyComplete();

        verify(submissionRepository, times(1))
                .findAllByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, mockModuleName);
    }

    @Test
    void findAllValidByUserIdAndModuleName_SubmissionsExist_ReturnsSubmissions() {

        final String mockModuleName = "id";
        final Submission mockSubmission = mock(Submission.class);

        when(submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, mockModuleName))
                .thenReturn(Mono.just(mockSubmission));
        StepVerifier.create(
                        submissionService.findAllValidByUserIdAndModuleName(
                                TestConstants.TEST_USER_ID, mockModuleName))
                .expectNext(mockSubmission)
                .verifyComplete();

        verify(submissionRepository, times(1))
                .findAllByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, mockModuleName);
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
                        clock);

        mockUser = mock(UserEntity.class);
        mockModule = mock(ModuleEntity.class);
        mockTeam = mock(TeamEntity.class);
    }

    @Test
    void submit_InvalidFlag_ReturnsInvalidSubmission() {
        final String mockSubmissionId = "submissionId";

        final String flag = "invalidFlag";

        final Clock fixedClock =
                Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

        setClock(fixedClock);

        when(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .thenReturn(Mono.just(false));

        when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
                .thenReturn(Mono.just(false));

        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(
                        user ->
                                Mono.just(
                                        user.getArgument(0, Submission.class)
                                                .withId(mockSubmissionId)));

        when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
        when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

        when(mockModule.isOpen()).thenReturn(true);

        StepVerifier.create(
                        submissionService.submitFlag(
                                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .assertNext(
                        submission -> {
                            assertThat(submission.getId()).isEqualTo(mockSubmissionId);
                            assertThat(submission.getUserId())
                                    .isEqualTo(TestConstants.TEST_USER_ID);
                            assertThat(submission.getModuleId())
                                    .isEqualTo(TestConstants.TEST_MODULE_ID);
                            assertThat(submission.getFlag()).isEqualTo(flag);
                            assertThat(submission.getTime())
                                    .isEqualTo(LocalDateTime.now(fixedClock));
                            assertThat(submission.isValid()).isFalse();
                        })
                .verifyComplete();

        verify(flagHandler, times(1))
                .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag);
        verify(submissionRepository, times(1))
                .existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }

    // TODO: module does not exist error handling

    @Test
    void submit_ModuleAlreadySolvedByUser_ReturnsModuleAlreadySolvedException() {

        final String flag = "validFlag";
        final UserEntity mockUser = mock(UserEntity.class);
        final ModuleEntity mockModule = mock(ModuleEntity.class);

        final Clock fixedClock =
                Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.systemDefault());

        setClock(fixedClock);

        when(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .thenReturn(Mono.just(true));

        when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
                .thenReturn(Mono.just(true));

        when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
        when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

        StepVerifier.create(
                        submissionService.submitFlag(
                                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .expectError(ModuleAlreadySolvedException.class)
                .verify();

        verify(flagHandler, times(1))
                .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag);
        verify(submissionRepository, times(1))
                .existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
    }

    @Test
    void submit_ValidFlag_ReturnsValidSubmission() {
        final String mockSubmissionId = "sub-id";

        final String flag = "validFlag";

        final Clock fixedClock =
                Clock.fixed(Instant.parse("2000-01-01T10:00:00.00Z"), ZoneId.of("Z"));

        setClock(fixedClock);

        when(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .thenReturn(Mono.just(true));

        when(submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID))
                .thenReturn(Mono.just(false));

        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(
                        user ->
                                Mono.just(
                                        user.getArgument(0, Submission.class)
                                                .withId(mockSubmissionId)));

        when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
        when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

        when(mockModule.isOpen()).thenReturn(true);

        StepVerifier.create(
                        submissionService.submitFlag(
                                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag))
                .assertNext(
                        submission -> {
                            assertThat(submission.getId()).isEqualTo(mockSubmissionId);
                            assertThat(submission.getUserId())
                                    .isEqualTo(TestConstants.TEST_USER_ID);
                            assertThat(submission.getModuleId())
                                    .isEqualTo(TestConstants.TEST_MODULE_ID);
                            assertThat(submission.getFlag()).isEqualTo(flag);
                            assertThat(submission.getTime())
                                    .isEqualTo(LocalDateTime.now(fixedClock));
                            assertThat(submission.isValid()).isTrue();
                        })
                .verifyComplete();

        verify(flagHandler, times(1))
                .verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, flag);
        verify(submissionRepository, times(1))
                .existsByUserIdAndModuleIdAndIsValidTrue(
                        TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID);
        verify(submissionRepository, times(1)).save(any(Submission.class));
    }
}
