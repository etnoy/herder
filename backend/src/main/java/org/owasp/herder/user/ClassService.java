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
package org.owasp.herder.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateClassNameException;
import org.owasp.herder.model.ClassEntity;
import org.owasp.herder.validation.ValidClassId;
import org.owasp.herder.validation.ValidClassName;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Validated
@Service
public class ClassService {
  private final ClassRepository classRepository;

  public Mono<Long> count() {
    return classRepository.count();
  }

  public Mono<ClassEntity> create(@ValidClassName final String className) {
    if (className == null) {
      return Mono.error(new NullPointerException());
    }

    if (className.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

    log.debug("Creating class with name " + className);

    return Mono
      .just(className)
      .filterWhen(this::doesNotExistByName)
      .switchIfEmpty(
        Mono.error(new DuplicateClassNameException("Class name already exists"))
      )
      .flatMap(
        name -> classRepository.save(ClassEntity.builder().name(name).build())
      );
  }

  private Mono<Boolean> doesNotExistByName(
    @ValidClassName final String className
  ) {
    return classRepository
      .findByName(className)
      .map(u -> false)
      .defaultIfEmpty(true);
  }

  public Mono<Boolean> existsById(@ValidClassId final String classId) {
    return classRepository.existsById(classId);
  }

  public Mono<ClassEntity> getById(@ValidClassId final String classId) {
    return Mono
      .just(classId)
      .filterWhen(classRepository::existsById)
      .switchIfEmpty(Mono.error(new ClassIdNotFoundException()))
      .flatMap(classRepository::findById);
  }

  public Mono<ClassEntity> setName(
    @ValidClassId final String classId,
    @ValidClassName final String name
  ) {
    Mono<String> nameMono = Mono
      .just(name)
      .filterWhen(this::doesNotExistByName)
      .switchIfEmpty(
        Mono.error(new DuplicateClassNameException("Class name already exists"))
      );

    return Mono
      .just(classId)
      .flatMap(this::getById)
      .zipWith(nameMono)
      .map(tuple -> tuple.getT1().withName(tuple.getT2()))
      .flatMap(classRepository::save);
  }
}
