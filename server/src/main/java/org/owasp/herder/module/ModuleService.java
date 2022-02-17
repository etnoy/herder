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

import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.EmptyModuleNameException;
import org.owasp.herder.exception.InvalidFlagException;
import org.owasp.herder.exception.InvalidModuleIdException;
import org.owasp.herder.exception.InvalidModuleLocatorException;
import org.owasp.herder.exception.ModuleIdNotFoundException;
import org.owasp.herder.exception.ModuleNameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public final class ModuleService {

  private final ModuleRepository moduleRepository;

  private final ModuleTagRepository moduleTagRepository;

  private final KeyService keyService;

  public Mono<Long> count() {
    return moduleRepository.count();
  }

  public Mono<String> create(final String name, final String locator) {
    if (locator == null) {
      return Mono.error(new NullPointerException("Module locator cannot be null"));
    }
    if (locator.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    // TODO: null check on name
    log.info("Creating new module " + locator);

    return Mono.just(locator)
        .filterWhen(this::doesNotExistByName)
        .switchIfEmpty(
            Mono.error(
                new DuplicateModuleNameException(
                    String.format("Module name %s already exists", locator))))
        .map(
            exists ->
                ModuleEntity.builder()
                    .isOpen(true)
                    .locator(locator)
                    .name(name)
                    .key(keyService.generateRandomBytes(16))
                    .build())
        .flatMap(moduleRepository::save)
        .doOnSuccess(m -> log.trace("Created module with name " + locator))
        .map(ModuleEntity::getId);
  }

  private Mono<Boolean> doesNotExistByName(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    return findByName(moduleName).map(u -> false).defaultIfEmpty(true);
  }

  public Mono<Boolean> existsByName(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    return findByName(moduleName).map(u -> true).defaultIfEmpty(false);
  }

  private ModuleListItem filterEmptyTags(final ModuleListItem item) {
    if (item.getTags().length == 0) {
      return item.withTags(null);
    }
    return item;
  }

  public Flux<ModuleEntity> findAll() {
    return moduleRepository.findAll();
  }

  public Flux<ModuleEntity> findAllOpen() {
    return moduleRepository.findAllOpen();
  }

  public Flux<ModuleListItem> findAllOpenWithSolutionStatus(final String userId) {
    return moduleRepository.findAllOpenWithSolutionStatus(userId).map(this::filterEmptyTags);
  }

  public Flux<ModuleTag> findAllTagsByModuleName(final String moduleName) {
    if (moduleName == null) {
      return Flux.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Flux.error(new EmptyModuleNameException());
    }
    return moduleTagRepository.findAllByModuleId(moduleName);
  }

  public Flux<ModuleTag> findAllTagsByModuleNameAndTagName(
      final String moduleName, final String tagName) {
    if (moduleName == null) {
      return Flux.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Flux.error(new EmptyModuleNameException());
    }
    return moduleTagRepository.findAllByModuleIdAndName(moduleName, tagName);
  }

  public Mono<ModuleEntity> findByName(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    log.trace("Find module with name " + moduleName);
    return moduleRepository.findByName(moduleName);
  }

  public Mono<ModuleEntity> findById(final String moduleId) {
    if (moduleId == null) {
      return Mono.error(new NullPointerException("Module id cannot be null"));
    }
    if (moduleId.isEmpty()) {
      return Mono.error(new InvalidModuleIdException());
    }
    log.trace("Finding module with id " + moduleId);
    return moduleRepository.findById(moduleId);
  }

  public Mono<ModuleListItem> findByIdWithSolutionStatus(
      final String userId, final String moduleId) {
    return moduleRepository.findByIdWithSolutionStatus(userId, moduleId).map(this::filterEmptyTags);
  }

  public Flux<ModuleTag> saveTags(final Iterable<ModuleTag> tags) {
    return moduleTagRepository.saveAll(tags);
  }

  public Mono<ModuleEntity> setDynamicFlag(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    return findByName(moduleName)
        .switchIfEmpty(Mono.error(new ModuleNameNotFoundException()))
        .map(module -> module.withFlagStatic(false))
        .flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setStaticFlag(final String moduleId, final String staticFlag) {
    if (moduleId == null) {
      return Mono.error(new NullPointerException("Module id cannot be null"));
    }
    if (moduleId.isEmpty()) {
      return Mono.error(new InvalidModuleIdException());
    }
    if (staticFlag == null) {
      return Mono.error(new NullPointerException("Flag cannot be null"));
    } else if (staticFlag.isEmpty()) {
      return Mono.error(new InvalidFlagException("Flag cannot be empty"));
    }

    return findById(moduleId)
        .switchIfEmpty(Mono.error(new ModuleIdNotFoundException()))
        .map(module -> module.withFlagStatic(true).withStaticFlag(staticFlag))
        .flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> findByLocator(final String moduleLocator) {
    if (moduleLocator == null) {
      return Mono.error(new NullPointerException("Module locator cannot be null"));
    }
    if (moduleLocator.isEmpty()) {
      return Mono.error(new InvalidModuleLocatorException());
    }
    log.trace("Finding module with id " + moduleLocator);
    return moduleRepository.findByLocator(moduleLocator);
  }
}
