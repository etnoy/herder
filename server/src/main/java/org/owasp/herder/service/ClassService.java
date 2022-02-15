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
package org.owasp.herder.service;

import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateClassNameException;
import org.owasp.herder.exception.InvalidClassIdException;
import org.owasp.herder.model.ClassEntity;
import org.owasp.herder.user.ClassRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public final class ClassService {

  private final ClassRepository classRepository;

  public Mono<Long> count() {
    return classRepository.count();
  }

  public Mono<ClassEntity> create(final String name) {
    if (name == null) {
      return Mono.error(new NullPointerException());
    }

    if (name.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

    log.debug("Creating class with name " + name);

    return Mono.just(name)
        .filterWhen(this::doesNotExistByName)
        .switchIfEmpty(Mono.error(new DuplicateClassNameException("Class name already exists")))
        .flatMap(className -> classRepository.save(ClassEntity.builder().name(className).build()));
  }

  private Mono<Boolean> doesNotExistByName(final String name) {
    return classRepository.findByName(name).map(u -> false).defaultIfEmpty(true);
  }

  public Mono<Boolean> existsById(final String classId) {
    return classRepository.existsById(classId);
  }

  public Mono<ClassEntity> getById(final String classId) {
    if (classId == null) {
      return Mono.error(new InvalidClassIdException("Class id can't be null"));
    }
    if (classId.isEmpty()) {
      return Mono.error(new InvalidClassIdException("Class id can't be empty"));
    }
    return Mono.just(classId)
        .filterWhen(classRepository::existsById)
        .switchIfEmpty(Mono.error(new ClassIdNotFoundException()))
        .flatMap(classRepository::findById);
  }

  public Mono<ClassEntity> setName(final String classId, final String name) {
    if (name == null) {
      return Mono.error(new IllegalArgumentException("Class name can't be null"));
    }

    Mono<String> nameMono =
        Mono.just(name)
            .filterWhen(this::doesNotExistByName)
            .switchIfEmpty(
                Mono.error(new DuplicateClassNameException("Class name already exists")));

    return Mono.just(classId)
        .flatMap(this::getById)
        .zipWith(nameMono)
        .map(tuple -> tuple.getT1().withName(tuple.getT2()))
        .flatMap(classRepository::save);
  }
}
