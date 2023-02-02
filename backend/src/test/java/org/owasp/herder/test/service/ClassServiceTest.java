/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateClassNameException;
import org.owasp.herder.model.ClassEntity;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassRepository;
import org.owasp.herder.user.ClassService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassService unit tests")
class ClassServiceTest extends BaseTest {

  private ClassService classService;

  @Mock
  private ClassRepository classRepository;

  final ClassEntity mockClass = mock(ClassEntity.class);

  @Test
  @DisplayName("Can count classes")
  void count_FiniteNumberOfClasses_ReturnsCount() {
    final long mockedClassCount = 156L;

    when(classRepository.count()).thenReturn(Mono.just(mockedClassCount));

    StepVerifier.create(classService.count()).expectNext(mockedClassCount).verifyComplete();
  }

  @Test
  @DisplayName("Can error when creating class with duplicate name")
  void create_DuplicateName_ReturnsDuplicateClassNameException() {
    when(classRepository.findByName(TestConstants.TEST_CLASS_NAME)).thenReturn(Mono.just(mockClass));

    StepVerifier
      .create(classService.create(TestConstants.TEST_CLASS_NAME))
      .expectError(DuplicateClassNameException.class)
      .verify();
  }

  @Test
  @DisplayName("Can create a class")
  void create_ValidData_CreatesClass() {
    when(classRepository.findByName(TestConstants.TEST_CLASS_NAME)).thenReturn(Mono.empty());

    when(classRepository.save(any(ClassEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ClassEntity.class).withId(TestConstants.TEST_CLASS_ID)));

    StepVerifier
      .create(classService.create(TestConstants.TEST_CLASS_NAME))
      .assertNext(createdClass -> {
        assertThat(createdClass).isInstanceOf(ClassEntity.class);
        assertThat(createdClass.getName()).isEqualTo(TestConstants.TEST_CLASS_NAME);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if class exists")
  void existsById_ExistingClassId_ReturnsTrue() {
    when(classRepository.existsById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.just(true));

    StepVerifier.create(classService.existsById(TestConstants.TEST_CLASS_ID)).expectNext(true).verifyComplete();
  }

  @Test
  @DisplayName("Can check if class does not exist")
  void existsById_NonExistentClassId_ReturnsFalse() {
    when(classRepository.existsById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.just(false));

    StepVerifier.create(classService.existsById(TestConstants.TEST_CLASS_ID)).expectNext(false).verifyComplete();
  }

  @Test
  @DisplayName("Can get class by id")
  void getById_ValidClassId_CallsRepository() {
    final ClassEntity testClass = TestConstants.TEST_CLASS_ENTITY;

    when(classRepository.findById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.just(testClass));

    StepVerifier.create(classService.getById(TestConstants.TEST_CLASS_ID)).expectNext(testClass).verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting class by id that does not exist")
  void getById_NonexistentClassId_Errors() {
    when(classRepository.findById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.empty());

    StepVerifier
      .create(classService.getById(TestConstants.TEST_CLASS_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof ClassIdNotFoundException &&
        throwable.getMessage().equals(String.format("Class \"%s\" not found", TestConstants.TEST_CLASS_ID))
      )
      .verify();
  }

  @Test
  @DisplayName("Setting a class name to a name that is taken should return DuplicateClassNameException")
  void setName_DuplicateName_ReturnsDuplicateClassNameException() {
    final String newName = "newTestClass";

    when(classRepository.findById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.just(mockClass));
    when(classRepository.findByName(newName)).thenReturn(Mono.just(mockClass));

    StepVerifier
      .create(classService.setName(TestConstants.TEST_CLASS_ID, newName))
      .expectError(DuplicateClassNameException.class)
      .verify();
  }

  @Test
  @DisplayName("Setting the name of a class should change the class name")
  void setName_ValidName_SetsName() {
    final ClassEntity testClass = TestConstants.TEST_CLASS_ENTITY;
    final String newName = "newTestClass";

    when(classRepository.findById(TestConstants.TEST_CLASS_ID)).thenReturn(Mono.just(testClass));
    when(classRepository.findByName(newName)).thenReturn(Mono.empty());
    when(classRepository.save(any(ClassEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier
      .create(classService.setName(TestConstants.TEST_CLASS_ID, newName))
      .assertNext(classEntity -> {
        assertThat(classEntity.getName()).isEqualTo(newName);
      })
      .verifyComplete();

    final ArgumentCaptor<ClassEntity> argument = ArgumentCaptor.forClass(ClassEntity.class);
    verify(classRepository).save(argument.capture());
    assertThat(argument.getValue().getName()).isEqualTo(newName);
  }

  @BeforeEach
  void setup() {
    classService = new ClassService(classRepository);
  }
}
