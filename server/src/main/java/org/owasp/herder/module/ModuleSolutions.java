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
package org.owasp.herder.module;

import lombok.RequiredArgsConstructor;

import org.owasp.herder.exception.EmptyModuleNameException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.module.ModuleListItem.ModuleListItemBuilder;
import org.owasp.herder.scoring.SubmissionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Component
public final class ModuleSolutions {

  private final ModuleService moduleService;

  private final SubmissionService submissionService;

  public Flux<ModuleListItem> findOpenModulesByUserIdWithSolutionStatus(final long userId) {
    if (userId <= 0) {
      return Flux.error(new InvalidUserIdException("User id must be a strictly positive integer"));
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
                    .map(
                        module -> {
                          final String moduleName = module.getName();
                          // For each module, construct a module list item
                          moduleListItemBuilder.name(moduleName);
                          // Check if this module id is finished
                          moduleListItemBuilder.isSolved(finishedModules.contains(moduleName));
                          // Build the module list item and return
                          return moduleListItemBuilder.build();
                        }));
  }

  public Mono<ModuleListItem> findOpenModuleByIdWithSolutionStatus(
      final long userId, final String moduleName) {
    if (userId <= 0) {
      return Mono.error(new InvalidUserIdException("User id must be a strictly positive integer"));
    }
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException("Module name cannot be empty"));
    }

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();

    final Mono<Module> moduleMono = moduleService.findByName(moduleName).filter(Module::isOpen);

    return moduleMono
        .map(Module::getName)
        // Find all valid submissions by this user
        .flatMap(openModuleName -> userHasSolvedThisModule(userId, openModuleName))
        .defaultIfEmpty(false)
        .zipWith(moduleMono)
        .map(
            tuple -> {
              final Module module = tuple.getT2();
              // For each module, construct a module list item
              moduleListItemBuilder.name(module.getName());
              moduleListItemBuilder.isSolved(tuple.getT1());
              // Build the module list item and return
              return moduleListItemBuilder.build();
            });
  }

  public Mono<ModuleListItem> findModuleByNameWithSolutionStatus(
      final long userId, final String moduleName) {

    final Mono<Module> moduleMono = moduleService.findByName(moduleName);

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
