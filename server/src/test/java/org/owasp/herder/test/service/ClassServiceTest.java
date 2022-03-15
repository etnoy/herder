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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateClassNameException;
import org.owasp.herder.model.ClassEntity;
import org.owasp.herder.user.ClassRepository;
import org.owasp.herder.user.ClassService;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassService unit tests")
class ClassServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ClassService classService;

  @Mock private ClassRepository classRepository;

  @Test
  @DisplayName("count() call the count function of ClassRepository and return its same value")
  void count_FiniteNumberOfClasses_ReturnsCount() {
    final long mockedClassCount = 156L;

    when(classRepository.count()).thenReturn(Mono.just(mockedClassCount));

    StepVerifier.create(classService.count()).expectNext(mockedClassCount).verifyComplete();

    verify(classRepository, times(1)).count();
  }

  @Test
  @DisplayName(
      "create() should return DuplicateClassNameException when the given name is already taken")
  void create_DuplicateName_ReturnsDuplicateClassNameException() {
    final String mockClassName = "TestClass";
    final ClassEntity mockClass = mock(ClassEntity.class);

    when(classRepository.findByName(mockClassName)).thenReturn(Mono.just(mockClass));

    StepVerifier.create(classService.create(mockClassName))
        .expectError(DuplicateClassNameException.class)
        .verify();
  }

  @Test
  @DisplayName("create() should return IllegalArgumentException when called with an empty name")
  void create_EmptyArgument_ReturnsIllegalArgumentException() {
    StepVerifier.create(classService.create(""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  @DisplayName("create() should return NullPointerException when called with null name")
  void create_NullArgument_ReturnsNullPointerException() {
    StepVerifier.create(classService.create(null)).expectError(NullPointerException.class).verify();
  }

  @Test
  @DisplayName("create() should return a class")
  void create_ValidData_CreatesClass() {
    final String mockClassName = "TestClass";
    final int mockClassId = 838;

    when(classRepository.findByName(mockClassName)).thenReturn(Mono.empty());

    when(classRepository.save(any(ClassEntity.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, ClassEntity.class).withId(mockClassId)));

    StepVerifier.create(classService.create(mockClassName))
        .assertNext(
            createdClass -> {
              assertThat(createdClass).isInstanceOf(ClassEntity.class);
              assertThat(createdClass.getName()).isEqualTo(mockClassName);
            })
        .verifyComplete();

    verify(classRepository, times(1)).findByName(mockClassName);
    verify(classRepository, times(1)).save(any(ClassEntity.class));
  }

  @Test
  @DisplayName("Checking if an existing class id exists should return true")
  void existsById_ExistingClassId_ReturnsTrue() {
    final String mockClassId = "id";

    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(true));

    StepVerifier.create(classService.existsById(mockClassId)).expectNext(true).verifyComplete();

    verify(classRepository, times(1)).existsById(mockClassId);
  }

  @Test
  @DisplayName("Checking if a non-existent class exists should return false")
  void existsById_NonExistentClassId_ReturnsFalse() {
    final String mockClassId = "id";

    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(false));

    StepVerifier.create(classService.existsById(mockClassId)).expectNext(false).verifyComplete();

    verify(classRepository, times(1)).existsById(mockClassId);
  }

  @Test
  @DisplayName("Getting a class id should return the correct class")
  void getById_ValidClassId_CallsRepository() {
    final ClassEntity mockClass = mock(ClassEntity.class);

    final String mockName = "TestClass";
    final String mockClassId = "id";

    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(mockClass.getName()).thenReturn(mockName);
    when(classRepository.findById(mockClassId)).thenReturn(Mono.just(mockClass));

    StepVerifier.create(classService.getById(mockClassId))
        .assertNext(
            classEntity -> {
              assertThat(classEntity.getName()).isEqualTo(mockName);
            })
        .verifyComplete();

    verify(classRepository, times(1)).findById(mockClassId);
  }

  @Test
  @DisplayName(
      "Setting a class name to a name that is taken should return DuplicateClassNameException")
  void setName_DuplicateName_ReturnsDuplicateClassNameException() {
    final ClassEntity mockClass = mock(ClassEntity.class);
    final String newName = "newTestClass";

    final String mockClassId = "id";

    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(classRepository.findById(mockClassId)).thenReturn(Mono.just(mockClass));
    when(classRepository.findByName(newName)).thenReturn(Mono.just(mockClass));

    StepVerifier.create(classService.setName(mockClassId, newName))
        .expectError(DuplicateClassNameException.class)
        .verify();
  }

  @Test
  @DisplayName(
      "Setting the name of a class that does not exist should return ClassIdNotFoundException")
  void setName_NonExistentId_ReturnsClassIdNotFoundException() {
    final String newName = "newTestClass";

    final String mockClassId = "id";

    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(false));

    StepVerifier.create(classService.setName(mockClassId, newName))
        .expectError(ClassIdNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Setting the name of a class should change the class name")
  void setName_ValidName_SetsName() {
    final ClassEntity mockClass = mock(ClassEntity.class);
    final ClassEntity mockClassWithName = mock(ClassEntity.class);

    final String newName = "newTestClass";

    final String mockClassId = "id";

    when(classRepository.findById(mockClassId)).thenReturn(Mono.just(mockClass));
    when(classRepository.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(classRepository.findByName(newName)).thenReturn(Mono.empty());
    when(mockClass.withName(newName)).thenReturn(mockClassWithName);
    when(mockClassWithName.getName()).thenReturn(newName);

    when(classRepository.save(mockClassWithName)).thenReturn(Mono.just(mockClassWithName));

    StepVerifier.create(classService.setName(mockClassId, newName))
        .assertNext(
            classEntity -> {
              assertThat(classEntity.getName()).isEqualTo(newName);
            })
        .verifyComplete();

    verify(classRepository, times(1)).findById(mockClassId);
    verify(classRepository, times(1)).findByName(newName);
  }

  @BeforeEach
  private void setUp() {
    classService = new ClassService(classRepository);
  }
}
