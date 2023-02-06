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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.DuplicateModuleLocatorException;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ModuleListRepository;
import org.owasp.herder.user.UserRepository;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleService unit tests")
class ModuleServiceTest extends BaseTest {

  ModuleService moduleService;

  @Mock
  ModuleRepository moduleRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  ModuleListRepository moduleListRepository;

  @Mock
  KeyService keyService;

  @Mock
  ApplicationContext applicationContext;

  @Test
  @DisplayName("Can count the number of modules")
  void count_NoArgument_ReturnsCount() {
    final long testModuleCount = 75L;

    when(moduleRepository.count()).thenReturn(Mono.just(testModuleCount));

    StepVerifier.create(moduleService.count()).expectNext(testModuleCount).verifyComplete();
  }

  @Test
  @DisplayName("Can create a new module")
  void create_NewModuleLocator_Succeeds() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.empty());
    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.empty());

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(module -> Mono.just(module.getArgument(0, ModuleEntity.class).withId(TestConstants.TEST_MODULE_ID)));

    StepVerifier
      .create(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().getName()).isEqualTo(TestConstants.TEST_MODULE_NAME);
    assertThat(argument.getValue().getLocator()).isEqualTo(TestConstants.TEST_MODULE_LOCATOR);
  }

  @Test
  @DisplayName("Can error when creating a module with duplicate module locator")
  void create_ModuleLocatorExists_ReturnsDuplicateModuleLocatorException() {
    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(mock(ModuleEntity.class)));

    StepVerifier
      .create(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .expectError(DuplicateModuleLocatorException.class)
      .verify();
  }

  @Test
  @DisplayName("Can find all module")
  void findAll_ModulesExist_ReturnsModules() {
    final ModuleEntity mockModule1 = mock(ModuleEntity.class);
    final ModuleEntity mockModule2 = mock(ModuleEntity.class);
    final ModuleEntity mockModule3 = mock(ModuleEntity.class);

    when(moduleRepository.findAll()).thenReturn(Flux.just(mockModule1, mockModule2, mockModule3));

    StepVerifier
      .create(moduleService.findAll())
      .expectNext(mockModule1)
      .expectNext(mockModule2)
      .expectNext(mockModule3)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find all open modules")
  void findAllOpen_OpenModulesExist_ReturnsOpenModules() {
    final ModuleEntity mockModule1 = mock(ModuleEntity.class);
    final ModuleEntity mockModule2 = mock(ModuleEntity.class);
    final ModuleEntity mockModule3 = mock(ModuleEntity.class);

    when(moduleRepository.findAllOpen()).thenReturn(Flux.just(mockModule1, mockModule2, mockModule3));

    StepVerifier
      .create(moduleService.findAllOpen())
      .expectNext(mockModule1)
      .expectNext(mockModule2)
      .expectNext(mockModule3)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find a module by name")
  void findByName_ModuleNameExists_ReturnsModule() {
    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));
    StepVerifier
      .create(moduleService.findByName(TestConstants.TEST_MODULE_NAME))
      .expectNext(TestConstants.TEST_MODULE_ENTITY)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return existing dynamic flag of module if it was already set")
  void setDynamicFlag_FlagPreviouslySet_ReturnPreviousFlag() {
    final ModuleEntity mockModuleWithStaticFlag = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithDynamicFlag = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModuleWithStaticFlag));
    when(mockModuleWithStaticFlag.withFlagStatic(false)).thenReturn(mockModuleWithDynamicFlag);
    when(mockModuleWithDynamicFlag.getKey()).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(moduleRepository.save(mockModuleWithDynamicFlag)).thenReturn(Mono.just(mockModuleWithDynamicFlag));

    StepVerifier
      .create(moduleService.setDynamicFlag(TestConstants.TEST_MODULE_ID))
      .expectNextMatches(module -> module.getKey().equals(TestConstants.TEST_BYTE_ARRAY))
      .verifyComplete();
    verify(keyService, never()).generateRandomString(any(Integer.class));

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().getKey()).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
  }

  @Test
  @DisplayName("Can set dynamic flag of module")
  void setDynamicFlag_StaticFlagIsSet_SetsDynamicFlag() {
    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withFlagStatic(true)));
    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));

    StepVerifier
      .create(moduleService.setDynamicFlag(TestConstants.TEST_MODULE_ID))
      .expectNextMatches(module -> !module.isFlagStatic())
      .verifyComplete();

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().isFlagStatic()).isFalse();
  }

  @Test
  @DisplayName("Can set base score of module")
  void setBaseScore_ValidBaseScore_SetsBaseScore() {
    final int newBaseScore = 100;

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));

    StepVerifier
      .create(moduleService.setBaseScore(TestConstants.TEST_MODULE_ID, newBaseScore))
      .expectNextMatches(module -> module.getBaseScore() == newBaseScore)
      .verifyComplete();

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().getBaseScore()).isEqualTo(newBaseScore);
  }

  @Test
  @DisplayName("Can set bonus scores of module")
  void setBonusScores_ValidBonusScores_SetsBaseScore() {
    List<Integer> bonusScores = new ArrayList<Integer>();

    bonusScores.add(100);
    bonusScores.add(50);
    bonusScores.add(10);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));

    StepVerifier
      .create(moduleService.setBonusScores(TestConstants.TEST_MODULE_ID, bonusScores))
      .expectNextMatches(module -> module.getBonusScores().equals(bonusScores))
      .verifyComplete();
  }

  @Test
  @DisplayName("Can set module static flag")
  void setStaticFlag_ValidStaticFlag_SetsFlagToStatic() {
    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));
    StepVerifier
      .create(moduleService.setStaticFlag(TestConstants.TEST_MODULE_ID, TestConstants.TEST_STATIC_FLAG))
      .expectNextMatches(module ->
        module.isFlagStatic() && module.getStaticFlag().equals(TestConstants.TEST_STATIC_FLAG)
      )
      .verifyComplete();
  }

  @Test
  @DisplayName("Can close module")
  void close_ModuleOpen_ClosesModule() {
    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withOpen(true)));

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));

    StepVerifier
      .create(moduleService.close(TestConstants.TEST_MODULE_ID))
      .expectNextMatches(module -> !module.isOpen())
      .verifyComplete();

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().isOpen()).isFalse();
  }

  @Test
  @DisplayName("Can open module")
  void open_ModuleClosed_OpensModule() {
    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withOpen(false)));

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class)));

    StepVerifier
      .create(moduleService.open(TestConstants.TEST_MODULE_ID))
      .expectNextMatches(module -> module.isOpen())
      .verifyComplete();

    final ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().isOpen()).isTrue();
  }

  @Test
  @DisplayName("Can check if module exists by locator")
  void existsByLocator_ModuleExists_ReturnsTrue() {
    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    StepVerifier
      .create(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if module does not exist by locator")
  void existsByLocator_ModuleDoesNotExist_ReturnsFalse() {
    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.empty());

    StepVerifier
      .create(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if module exists by name")
  void existsByName_ModuleExists_ReturnsTrue() {
    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY));

    StepVerifier.create(moduleService.existsByName(TestConstants.TEST_MODULE_NAME)).expectNext(true).verifyComplete();
  }

  @Test
  @DisplayName("Can check if module does not exist by name")
  void existsByName_ModuleDoesNotExist_ReturnsFalse() {
    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.empty());

    StepVerifier.create(moduleService.existsByName(TestConstants.TEST_MODULE_NAME)).expectNext(false).verifyComplete();
  }

  @BeforeEach
  void setup() {
    moduleService = new ModuleService(moduleRepository, keyService, moduleListRepository, userRepository);
  }
}
