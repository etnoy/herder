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
package org.owasp.herder.scoring;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.exception.ModuleClosedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.SanitizedRankedSubmission.SanitizedRankedSubmissionBuilder;
import org.owasp.herder.scoring.Submission.SubmissionBuilder;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.owasp.herder.validation.ValidFlag;
import org.owasp.herder.validation.ValidModuleId;
import org.owasp.herder.validation.ValidModuleLocator;
import org.owasp.herder.validation.ValidModuleName;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Validated
@RequiredArgsConstructor
public class SubmissionService {

  private final SubmissionRepository submissionRepository;

  private final RankedSubmissionRepository rankedSubmissionRepository;

  private final FlagHandler flagHandler;

  private final UserService userService;

  private final ModuleService moduleService;

  private final Clock clock;

  public Flux<SanitizedRankedSubmission> findAllRankedByModuleLocator(@ValidModuleLocator final String moduleLocator) {
    return rankedSubmissionRepository.findAllByModuleLocator(moduleLocator).map(this::sanitizeRankedSubmission);
  }

  public Flux<SanitizedRankedSubmission> findAllRankedByTeamId(@ValidTeamId final String teamId) {
    return rankedSubmissionRepository.findAllByTeamId(teamId).map(this::sanitizeRankedSubmission);
  }

  public Flux<SanitizedRankedSubmission> findAllRankedByUserId(@ValidUserId final String userId) {
    return rankedSubmissionRepository.findAllByUserId(userId).map(this::sanitizeRankedSubmission);
  }

  public Flux<Submission> findAllSubmissions() {
    return submissionRepository.findAll();
  }

  public Flux<Submission> findAllValidByUserId(@ValidUserId final String userId) {
    return submissionRepository.findAllByUserIdAndIsValidTrue(userId);
  }

  public Mono<Submission> findAllValidByUserIdAndModuleName(
    @ValidUserId final String userId,
    @ValidModuleName final String moduleName
  ) {
    return submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(userId, moduleName);
  }

  private SanitizedRankedSubmission sanitizeRankedSubmission(final RankedSubmission rankedSubmission) {
    final SanitizedRankedSubmissionBuilder builder = SanitizedRankedSubmission.builder();

    if (rankedSubmission.getTeam() != null) {
      final TeamEntity team = rankedSubmission.getTeam();
      builder.id(team.getId());
      builder.displayName(team.getDisplayName());
      builder.solverType(SolverType.TEAM);
    } else if (rankedSubmission.getUser() != null) {
      final UserEntity user = rankedSubmission.getUser();
      builder.id(user.getId());
      builder.displayName(user.getDisplayName());
      builder.solverType(SolverType.USER);
    } else {
      throw new IllegalArgumentException("Ranked submission is missing user or team");
    }
    builder.baseScore(rankedSubmission.getBaseScore());
    builder.bonusScore(rankedSubmission.getBonusScore());
    builder.score(rankedSubmission.getScore());
    builder.time(rankedSubmission.getTime());
    builder.flag(rankedSubmission.getFlag());
    builder.rank(rankedSubmission.getRank());

    builder.moduleLocator(rankedSubmission.getModule().getLocator());
    builder.moduleName(rankedSubmission.getModule().getName());

    return builder.build();
  }

  public Mono<Void> setTeamIdOfUserSubmissions(@ValidUserId final String userId, @ValidUserId final String teamId) {
    final Flux<Submission> updatedSubmissions = submissionRepository
      .findAllByUserId(userId)
      .flatMap(submission -> {
        if (submission.getTeamId() != null && teamId != null) {
          return Mono.error(
            new IllegalStateException(
              String.format(
                "Ranked submission for user \"%s\" and module \"%s\" already has team id set",
                submission.getUserId(),
                submission.getModuleId()
              )
            )
          );
        } else {
          return Flux.just(submission);
        }
      })
      .map(submission -> submission.withTeamId(teamId));

    return submissionRepository.saveAll(updatedSubmissions).then();
  }

  public Mono<Void> clearTeamIdOfUserSubmissions(@ValidUserId final String userId) {
    return setTeamIdOfUserSubmissions(userId, null);
  }

  public Mono<Submission> submitFlag(
    @ValidUserId final String userId,
    @ValidModuleId final String moduleId,
    @ValidFlag final String flag
  ) {
    final SubmissionBuilder submissionBuilder = Submission.builder();

    submissionBuilder.flag(flag);
    submissionBuilder.time(LocalDateTime.now(clock));

    return flagHandler // Check if flag is correct
      .verifyFlag(userId, moduleId, flag)
      .map(submissionBuilder::isValid)
      // Has this module been solved by this user? In that case, throw exception.
      .filterWhen(u -> validSubmissionDoesNotExistByUserIdAndModuleId(userId, moduleId))
      .switchIfEmpty(
        Mono.error(
          new ModuleAlreadySolvedException(
            String.format("User %s has already finished module with id %s", userId, moduleId)
          )
        )
      )
      // Otherwise, build a submission and save it in db
      .zipWith(moduleService.getById(moduleId))
      .filter(tuple -> tuple.getT2().isOpen())
      .switchIfEmpty(
        Mono.error(new ModuleClosedException(String.format("Module %s is closed for submissions", moduleId)))
      )
      .map(tuple -> tuple.getT1().moduleId(moduleId))
      .zipWith(userService.getById(userId))
      .map(tuple -> tuple.getT1().userId(userId).teamId(tuple.getT2().getTeamId()))
      .map(SubmissionBuilder::build)
      .flatMap(submissionRepository::save);
  }

  private Mono<Boolean> validSubmissionDoesNotExistByUserIdAndModuleId(
    @ValidUserId final String userId,
    @ValidModuleName final String moduleId
  ) {
    return submissionRepository.existsByUserIdAndModuleIdAndIsValidTrue(userId, moduleId).map(exists -> !exists);
  }

  public Mono<Void> refreshSubmissionRanks() {
    return submissionRepository.refreshSubmissionRanks().then();
  }
}
