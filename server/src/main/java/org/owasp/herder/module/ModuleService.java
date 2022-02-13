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

  public Flux<ModuleTag> saveTags(final Iterable<ModuleTag> tags) {
    return moduleTagRepository.saveAll(tags);
  }

  public Flux<ModuleTag> findAllTagsByModuleName(final String moduleName) {
    if (moduleName == null) {
      return Flux.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Flux.error(new EmptyModuleNameException());
    }
    return moduleTagRepository.findAllByModuleName(moduleName);
  }

  public Flux<ModuleTag> findAllTagsByModuleNameAndTagName(
      final String moduleName, final String tagName) {
    if (moduleName == null) {
      return Flux.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Flux.error(new EmptyModuleNameException());
    }
    return moduleTagRepository.findAllByModuleNameAndName(moduleName, tagName);
  }

  public Mono<ModuleEntity> create(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    log.info("Creating new module " + moduleName);

    return Mono.just(moduleName)
        .filterWhen(this::doesNotExistByName)
        .switchIfEmpty(
            Mono.error(
                new DuplicateModuleNameException(
                    String.format("Module name %s already exists", moduleName))))
        .map(
            exists ->
                ModuleEntity.builder()
                    .isOpen(true)
                    .name(moduleName)
                    .key(keyService.generateRandomBytes(16))
                    .build())
        .flatMap(moduleRepository::save)
        .doOnSuccess(created -> log.trace("Created module with name " + moduleName));
  }

  public Flux<ModuleEntity> findAll() {
    return moduleRepository.findAll();
  }

  public Flux<ModuleEntity> findAllOpen() {
    return moduleRepository.findAllOpen();
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

  public Mono<Boolean> existsByName(final String moduleName) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    return findByName(moduleName).map(u -> true).defaultIfEmpty(false);
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

  public Mono<ModuleEntity> setStaticFlag(final String moduleName, final String staticFlag) {
    if (moduleName == null) {
      return Mono.error(new NullPointerException("Module name cannot be null"));
    }
    if (moduleName.isEmpty()) {
      return Mono.error(new EmptyModuleNameException());
    }
    if (staticFlag == null) {
      return Mono.error(new NullPointerException("Flag cannot be null"));
    } else if (staticFlag.isEmpty()) {
      return Mono.error(new InvalidFlagException("Flag cannot be empty"));
    }

    return findByName(moduleName)
        .switchIfEmpty(Mono.error(new ModuleNameNotFoundException()))
        .map(module -> module.withFlagStatic(true).withStaticFlag(staticFlag))
        .flatMap(moduleRepository::save);
  }
}
