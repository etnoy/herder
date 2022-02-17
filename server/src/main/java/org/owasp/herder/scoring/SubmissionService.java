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

import org.owasp.herder.exception.EmptyModuleNameException;
import org.owasp.herder.exception.InvalidModuleIdException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.scoring.Submission.SubmissionBuilder;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public final class SubmissionService {

  private final SubmissionRepository submissionRepository;

  private final FlagHandler flagHandler;

  private Clock clock;

  public SubmissionService(SubmissionRepository submissionRepository, FlagHandler flagHandler) {
    this.submissionRepository = submissionRepository;
    this.flagHandler = flagHandler;
    resetClock();
  }

  public Flux<Submission> findAllByModuleName(final String moduleName) {
    return submissionRepository.findAllByModuleId(moduleName);
  }

  public Flux<Submission> findAllValidByUserId(final String userId) {
    if (userId == null) {
      return Flux.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Flux.error(new InvalidUserIdException());
    }
    return submissionRepository.findAllValidByUserId(userId);
  }

  public Flux<RankedSubmission> findAllSubmissionsByModuleName(final String moduleId) {
    if (moduleId == null) {
      return Flux.error(new NullPointerException());
    }
    if (moduleId.isEmpty()) {
      return Flux.error(new InvalidModuleIdException());
    }
    return submissionRepository.findAllRankedByModuleId(moduleId);
  }

  public Flux<RankedSubmission> findAllRankedByUserId(final String userId) {
    if (userId == null) {
      return Flux.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Flux.error(new InvalidUserIdException());
    }

    return submissionRepository.findAllRankedByUserId(userId);
  }

  public Mono<Submission> findAllValidByUserIdAndModuleName(
      final String userId, final String moduleName) {
    if (moduleName == null || userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    return submissionRepository.findAllByUserIdAndModuleIdAndIsValidTrue(userId, moduleName);
  }

  public void resetClock() {
    this.clock = Clock.systemDefaultZone();
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public Mono<Submission> submit(final String userId, final String moduleId, final String flag) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }

    SubmissionBuilder submissionBuilder = Submission.builder();
    submissionBuilder.userId(userId);
    submissionBuilder.moduleId(moduleId);
    submissionBuilder.flag(flag);
    submissionBuilder.time(LocalDateTime.now(clock));
    return
    // Check if flag is correct
    flagHandler
        .verifyFlag(userId, moduleId, flag)
        // Get isValid field
        .map(submissionBuilder::isValid)
        // Has this module been solved by this user? In that case, throw exception.
        .filterWhen(u -> validSubmissionDoesNotExistByUserIdAndModuleId(userId, moduleId))
        .switchIfEmpty(
            Mono.error(
                new ModuleAlreadySolvedException(
                    String.format(
                        "User %s has already finished module with id %s", userId, moduleId))))
        // Otherwise, build a submission and save it in db
        .map(SubmissionBuilder::build)
        .flatMap(submissionRepository::save);
  }

  public Mono<Submission> submitValid(final String userId, final String moduleId) {
    if (moduleId == null || userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    if (moduleId.isEmpty()) {
      return Mono.error(new InvalidModuleIdException());
    }

    SubmissionBuilder submissionBuilder = Submission.builder();

    submissionBuilder.userId(userId);
    submissionBuilder.moduleId(moduleId);
    submissionBuilder.isValid(true);
    submissionBuilder.time(LocalDateTime.now(clock));

    return Mono.just(submissionBuilder)
        .filterWhen(u -> validSubmissionDoesNotExistByUserIdAndModuleId(userId, moduleId))
        .switchIfEmpty(
            Mono.error(
                new ModuleAlreadySolvedException(
                    String.format("User %s has already finished module %s", userId, moduleId))))
        // Otherwise, build a submission and save it in db
        .map(SubmissionBuilder::build)
        .flatMap(submissionRepository::save);
  }

  private Mono<Boolean> validSubmissionDoesNotExistByUserIdAndModuleId(
      final String userId, final String moduleName) {
    return submissionRepository
        .existsByUserIdAndModuleIdAndIsValidTrue(userId, moduleName)
        .map(exists -> !exists);
  }
}
