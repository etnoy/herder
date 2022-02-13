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
package org.owasp.herder.module;

import org.owasp.herder.exception.EmptyModuleNameException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.module.ModuleListItem.ModuleListItemBuilder;
import org.owasp.herder.scoring.SubmissionService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Component
public final class ModuleSolutions {

  private final ModuleService moduleService;

  private final SubmissionService submissionService;

  public Flux<ModuleListItem> findOpenModulesByUserIdWithSolutionStatus(final long userId) {
    if (userId <= 0) {
      return Flux.error(new InvalidUserIdException());
    }

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();
    return submissionService
        // Find all valid submissions by this user
        .findAllValidModuleNamesByUserId(userId)
        .flatMapMany(
            finishedModules ->
                // Get all modules
                moduleService
                    .findAllOpen()
                    .map(ModuleEntity::getName)
                    .flatMap(
                        moduleName ->
                            Mono.just(moduleName)
                                .zipWith(
                                    // Find all tags for the given module
                                    moduleService
                                        .findAllTagsByModuleName(moduleName)
                                        .map(
                                            tag ->
                                                NameValueTag.builder()
                                                    .name(tag.getName())
                                                    .value(tag.getValue())
                                                    .build())
                                        .collectList()))
                    .map(
                        tuple -> {
                          // For each module, construct a module list item
                          moduleListItemBuilder.name(tuple.getT1());
                          // Check if this module id is finished
                          moduleListItemBuilder.isSolved(finishedModules.contains(tuple.getT1()));
                          // Supply the list of tags (if any)
                          moduleListItemBuilder.tags(tuple.getT2());
                          // Build the list item
                          return moduleListItemBuilder.build();
                        }));
  }

  public Mono<ModuleListItem> findOpenModuleByIdWithSolutionStatus(
      final long userId, final String moduleName) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }
    if (moduleName == null) {
      return Mono.error(new NullPointerException());
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();

    final Mono<ModuleEntity> moduleMono =
        moduleService.findByName(moduleName).filter(ModuleEntity::isOpen);

    return moduleMono
        .map(ModuleEntity::getName)
        // Find all valid submissions by this user
        .flatMap(openModuleName -> userHasSolvedThisModule(userId, openModuleName))
        .defaultIfEmpty(false)
        .zipWith(moduleMono)
        .map(
            tuple -> {
              final ModuleEntity module = tuple.getT2();
              // For each module, construct a module list item
              moduleListItemBuilder.name(module.getName());
              moduleListItemBuilder.isSolved(tuple.getT1());
              // Build the module list item and return
              return moduleListItemBuilder.build();
            });
  }

  /**
   * Finds the module matching the given name and show its solution status for the given user id
   *
   * @param userId The user id to find the status for
   * @param moduleName The module to find
   * @return A {@link ModuleListItem} containing the module with the solution status
   */
  public Mono<ModuleListItem> findModuleByNameWithSolutionStatus(
      final long userId, final String moduleName) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException());
    }
    if (moduleName == null) {
      return Mono.error(new NullPointerException());
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    final Mono<ModuleEntity> moduleMono = moduleService.findByName(moduleName);

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();
    return moduleMono
        // Find all valid submissions by this user
        .zipWith(submissionService.findAllValidModuleNamesByUserId(userId))
        .map(
            tuple -> {
              moduleListItemBuilder.name(tuple.getT1().getName());
              moduleListItemBuilder.isSolved(tuple.getT2().contains(moduleName));
              return moduleListItemBuilder.build();
            });
  }

  private Mono<Boolean> userHasSolvedThisModule(final long userId, final String moduleName) {
    return submissionService
        .findAllValidByUserIdAndModuleName(userId, moduleName)
        .map(u -> true)
        .defaultIfEmpty(false);
  }
}
