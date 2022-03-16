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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.DuplicateModuleLocatorException;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.InvalidFlagException;
import org.owasp.herder.exception.ModuleNotFoundException;
import org.owasp.herder.validation.ValidModuleId;
import org.owasp.herder.validation.ValidModuleLocator;
import org.owasp.herder.validation.ValidModuleName;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@RequiredArgsConstructor
@Service
public class ModuleService {
  private final ModuleRepository moduleRepository;

  private final ModuleTagRepository moduleTagRepository;

  private final KeyService keyService;

  public Mono<ModuleEntity> close(@ValidModuleId final String moduleId) {
    return findById(moduleId)
        .switchIfEmpty(Mono.error(new ModuleNotFoundException()))
        .map(module -> module.withOpen(false))
        .flatMap(moduleRepository::save);
  }

  public Mono<Long> count() {
    return moduleRepository.count();
  }

  public Mono<String> create(
      @ValidModuleName final String moduleName, @ValidModuleLocator final String moduleLocator) {
    log.info("Creating new module " + moduleName + " with locator " + moduleLocator);

    return Mono.just(moduleLocator)
        // Check if locator already exists
        .filterWhen(this::doesNotExistByLocator)
        .switchIfEmpty(
            // Locator already exists, return error
            Mono.error(
                new DuplicateModuleLocatorException(
                    String.format("Module locator %s already exists", moduleLocator))))
        .map(exists -> moduleName)
        // Check if name already exists
        .filterWhen(this::doesNotExistByName)
        .switchIfEmpty(
            // Name exists, return error
            Mono.error(
                new DuplicateModuleNameException(
                    String.format("Module name %s already exists", moduleName))))
        .map(
            // Name and locator don't exist already, create new module
            exists ->
                ModuleEntity.builder()
                    .isOpen(true)
                    .locator(moduleLocator)
                    .name(moduleName)
                    // Generate the secret key
                    .key(keyService.generateRandomBytes(16))
                    .build())
        // Persist the module in the database
        .flatMap(moduleRepository::save)
        .doOnSuccess(
            module ->
                log.trace(
                    "Created module with name "
                        + module.getName()
                        + " and locator "
                        + module.getLocator()))
        // Return the created module id
        .map(ModuleEntity::getId);
  }

  private Mono<Boolean> doesNotExistByLocator(@ValidModuleLocator final String moduleLocator) {
    return findByLocator(moduleLocator).map(u -> false).defaultIfEmpty(true);
  }

  private Mono<Boolean> doesNotExistByName(final String moduleName) {
    return findByName(moduleName).map(u -> false).defaultIfEmpty(true);
  }

  public Mono<Boolean> existsByLocator(@ValidModuleLocator final String moduleLocator) {
    return findByLocator(moduleLocator).map(u -> true).defaultIfEmpty(false);
  }

  public Mono<Boolean> existsByName(@ValidModuleName final String moduleName) {
    return findByName(moduleName).map(u -> true).defaultIfEmpty(false);
  }

  public Flux<ModuleEntity> findAll() {
    return moduleRepository.findAll();
  }

  public Flux<ModuleEntity> findAllOpen() {
    return moduleRepository.findAllOpen();
  }

  public Flux<ModuleListItem> findAllOpenWithSolutionStatus(@ValidUserId final String userId) {
    return moduleRepository.findAllOpenWithSolutionStatus(userId);
  }

  public Flux<ModuleTag> findAllTagsByModuleId(@ValidModuleId final String moduleId) {
    return findById(moduleId)
        .switchIfEmpty(
            Mono.error(new ModuleNotFoundException("Could not find module with id " + moduleId)))
        .flatMapMany(m -> moduleTagRepository.findAllByModuleId(moduleId));
  }

  // TODO: validate tagname
  public Flux<ModuleTag> findAllTagsByModuleNameAndTagName(
      @ValidModuleName final String moduleName, final String tagName) {
    return moduleTagRepository.findAllByModuleIdAndName(moduleName, tagName);
  }

  public Mono<ModuleEntity> findById(@ValidModuleId final String moduleId) {
    log.trace("Finding module with id " + moduleId);
    return moduleRepository.findById(moduleId);
  }

  public Mono<ModuleEntity> findByLocator(@ValidModuleLocator final String moduleLocator) {
    log.trace("Finding module with id " + moduleLocator);
    return moduleRepository.findByLocator(moduleLocator);
  }

  public Mono<ModuleListItem> findByLocatorWithSolutionStatus(
      @ValidUserId final String userId, @ValidModuleLocator final String moduleLocator) {
    return moduleRepository.findByLocatorWithSolutionStatus(userId, moduleLocator);
  }

  public Mono<ModuleEntity> findByName(final String moduleName) {
    log.trace("Find module with name " + moduleName);
    return moduleRepository.findByName(moduleName);
  }

  public Mono<ModuleEntity> open(@ValidModuleId final String moduleId) {
    return findById(moduleId)
        .switchIfEmpty(Mono.error(new ModuleNotFoundException()))
        .map(module -> module.withOpen(true))
        .flatMap(moduleRepository::save);
  }

  public Flux<ModuleTag> saveTags(final Flux<ModuleTag> tags) {
    return moduleTagRepository.saveAll(tags);
  }

  public Mono<ModuleEntity> setDynamicFlag(@ValidModuleId final String moduleId) {
    return findById(moduleId)
        .switchIfEmpty(Mono.error(new ModuleNotFoundException()))
        .map(module -> module.withFlagStatic(false))
        .flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setStaticFlag(
      @ValidModuleId final String moduleId, final String staticFlag) {
    // TODO: validate flag
    if (staticFlag == null) {
      return Mono.error(new NullPointerException("Flag cannot be null"));
    } else if (staticFlag.isEmpty()) {
      return Mono.error(new InvalidFlagException("Flag cannot be empty"));
    }

    return findById(moduleId)
        .switchIfEmpty(Mono.error(new ModuleNotFoundException()))
        .map(module -> module.withFlagStatic(true).withStaticFlag(staticFlag))
        .flatMap(moduleRepository::save);
  }
}
