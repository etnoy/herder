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
import java.util.List;

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

  private final RankedSubmissionRepository rankedSubmissionRepository;

  private final FlagHandler flagHandler;

  private Clock clock;

  public SubmissionService(
      SubmissionRepository submissionRepository,
      RankedSubmissionRepository rankedSubmissionRepository,
      FlagHandler flagHandler) {
    this.submissionRepository = submissionRepository;
    this.rankedSubmissionRepository = rankedSubmissionRepository;
    this.flagHandler = flagHandler;
    resetClock();
  }

  public Flux<Submission> findAllByModuleName(final String moduleName) {
    return submissionRepository.findAllByModuleName(moduleName);
  }

  public Flux<Submission> findAllValidByUserId(final long userId) {
    if (userId <= 0) {
      return Flux.error(new InvalidUserIdException());
    }
    return submissionRepository.findAllValidByUserId(userId);
  }

  public Flux<RankedSubmission> findAllRankedByUserId(final long userId) {
    if (userId <= 0) {
      return Flux.error(new InvalidUserIdException());
    }
    return rankedSubmissionRepository.findAllByUserId(userId);
  }

  public Mono<Submission> findAllValidByUserIdAndModuleName(
      final long userId, final String moduleName) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }
    return submissionRepository.findAllValidByUserIdAndModuleName(userId, moduleName);
  }

  public Mono<List<String>> findAllValidModuleNamesByUserId(final long userId) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }
    return submissionRepository
        .findAllValidByUserId(userId)
        .map(Submission::getModuleName)
        .collectList();
  }

  public void resetClock() {
    this.clock = Clock.systemDefaultZone();
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public Mono<Submission> submit(final Long userId, final String moduleName, final String flag) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }
    SubmissionBuilder submissionBuilder = Submission.builder();
    submissionBuilder.userId(userId);
    submissionBuilder.moduleName(moduleName);
    submissionBuilder.flag(flag);
    submissionBuilder.time(LocalDateTime.now(clock));
    return
    // Check if flag is correct
    flagHandler
        .verifyFlag(userId, moduleName, flag)
        // Get isValid field
        .map(submissionBuilder::isValid)
        // Has this module been solved by this user? In that case, throw exception.
        .filterWhen(u -> validSubmissionDoesNotExistByUserIdAndModuleName(userId, moduleName))
        .switchIfEmpty(
            Mono.error(
                new ModuleAlreadySolvedException(
                    String.format("User %d has already finished module %s", userId, moduleName))))
        // Otherwise, build a submission and save it in db
        .map(SubmissionBuilder::build)
        .flatMap(submissionRepository::save);
  }

  public Mono<Submission> submitValid(final Long userId, final String moduleName) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }

    SubmissionBuilder submissionBuilder = Submission.builder();

    submissionBuilder.userId(userId);
    submissionBuilder.moduleName(moduleName);
    submissionBuilder.isValid(true);
    submissionBuilder.time(LocalDateTime.now(clock));

    return Mono.just(submissionBuilder)
        .filterWhen(u -> validSubmissionDoesNotExistByUserIdAndModuleName(userId, moduleName))
        .switchIfEmpty(
            Mono.error(
                new ModuleAlreadySolvedException(
                    String.format("User %d has already finished module %s", userId, moduleName))))
        // Otherwise, build a submission and save it in db
        .map(SubmissionBuilder::build)
        .flatMap(submissionRepository::save);
  }

  private Mono<Boolean> validSubmissionDoesNotExistByUserIdAndModuleName(
      final long userId, final String moduleName) {
    return submissionRepository
        .findAllValidByUserIdAndModuleName(userId, moduleName)
        .map(u -> false)
        .defaultIfEmpty(true);
  }
}
