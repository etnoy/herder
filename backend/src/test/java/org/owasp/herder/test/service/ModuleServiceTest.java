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
import org.owasp.herder.exception.InvalidFlagException;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ModuleListRepository;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleService unit tests")
class ModuleServiceTest extends BaseTest {

  final ModuleEntity mockModule = mock(ModuleEntity.class);

  ModuleService moduleService;

  @Mock
  ModuleRepository moduleRepository;

  @Mock
  ModuleListRepository moduleListRepository;

  @Mock
  KeyService keyService;

  @Mock
  ApplicationContext applicationContext;

  @Test
  void count_NoArgument_ReturnsCount() {
    final long mockedModuleCount = 75L;

    when(moduleRepository.count()).thenReturn(Mono.just(mockedModuleCount));

    StepVerifier.create(moduleService.count()).expectNext(mockedModuleCount).verifyComplete();
    verify(moduleRepository).count();
  }

  @Test
  void create_NewModuleLocator_Succeeds() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.empty());

    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.empty());

    when(moduleRepository.save(any(ModuleEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, ModuleEntity.class).withId(TestConstants.TEST_MODULE_ID)));

    StepVerifier
      .create(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();
  }

  @Test
  void create_ModuleLocatorExists_ReturnsDuplicateModuleLocatorException() {
    when(moduleRepository.findByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(mockModule));

    StepVerifier
      .create(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .expectError(DuplicateModuleLocatorException.class)
      .verify();
  }

  @Test
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
    verify(moduleRepository).findAll();
  }

  @Test
  void findAll_NoModulesExist_ReturnsEmpty() {
    when(moduleRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(moduleService.findAll()).verifyComplete();
    verify(moduleRepository).findAll();
  }

  @Test
  void findAllOpen_NoModulesExist_ReturnsEmpty() {
    when(moduleRepository.findAllOpen()).thenReturn(Flux.empty());
    StepVerifier.create(moduleService.findAllOpen()).verifyComplete();
    verify(moduleRepository).findAllOpen();
  }

  @Test
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
    verify(moduleRepository).findAllOpen();
  }

  @Test
  void findByName_ModuleNameExists_ReturnsModule() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));
    StepVerifier
      .create(moduleService.findByName(TestConstants.TEST_MODULE_NAME))
      .expectNext(mockModule)
      .verifyComplete();
    verify(moduleRepository).findByName(TestConstants.TEST_MODULE_NAME);
  }

  @Test
  void findByName_NonExistentModuleName_ReturnsEmpty() {
    when(moduleRepository.findByName(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.empty());
    StepVerifier.create(moduleService.findByName(TestConstants.TEST_MODULE_NAME)).verifyComplete();
    verify(moduleRepository).findByName(TestConstants.TEST_MODULE_NAME);
  }

  @Test
  void setDynamicFlag_FlagPreviouslySet_ReturnPreviousFlag() {
    final ModuleEntity mockModuleWithStaticFlag = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithDynamicFlag = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModuleWithStaticFlag));

    when(mockModuleWithStaticFlag.withFlagStatic(false)).thenReturn(mockModuleWithDynamicFlag);

    when(mockModuleWithDynamicFlag.getKey()).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(moduleRepository.save(mockModuleWithDynamicFlag)).thenReturn(Mono.just(mockModuleWithDynamicFlag));

    StepVerifier
      .create(moduleService.setDynamicFlag(TestConstants.TEST_MODULE_ID))
      .assertNext(module -> {
        assertThat(module.getKey()).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
      })
      .verifyComplete();
    verify(moduleRepository).save(any(ModuleEntity.class));
    verify(keyService, never()).generateRandomString(any(Integer.class));

    ArgumentCaptor<ModuleEntity> argument = ArgumentCaptor.forClass(ModuleEntity.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().getKey()).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
  }

  @Test
  void setDynamicFlag_StaticFlagIsSet_SetsDynamicFlag() {
    final ModuleEntity mockModuleWithStaticFlag = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithDynamicFlag = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModuleWithStaticFlag));

    when(mockModuleWithStaticFlag.withFlagStatic(false)).thenReturn(mockModuleWithDynamicFlag);

    when(mockModuleWithDynamicFlag.isFlagStatic()).thenReturn(false);

    when(moduleRepository.save(mockModuleWithDynamicFlag)).thenReturn(Mono.just(mockModuleWithDynamicFlag));

    StepVerifier
      .create(moduleService.setDynamicFlag(TestConstants.TEST_MODULE_ID))
      .assertNext(module -> {
        assertThat(module.isFlagStatic()).isFalse();
      })
      .verifyComplete();
    verify(mockModuleWithStaticFlag).withFlagStatic(false);
    verify(moduleRepository).save(mockModuleWithDynamicFlag);
  }

  @Test
  void setBaseScore_ValidBaseScore_SetsBaseScore() {
    final int baseScore = 100;

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithBaseScore = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(mockModule.withBaseScore(baseScore)).thenReturn(mockModuleWithBaseScore);

    when(moduleRepository.save(mockModuleWithBaseScore)).thenReturn(Mono.just(mockModuleWithBaseScore));

    StepVerifier
      .create(moduleService.setBaseScore(TestConstants.TEST_MODULE_ID, baseScore))
      .expectNext(mockModuleWithBaseScore)
      .verifyComplete();
  }

  @Test
  void setBaseScore_NegativeBaseScore_ReturnsInvalidScoreException() {
    final int invalidBaseScore = -1;

    StepVerifier
      .create(moduleService.setBaseScore(TestConstants.TEST_MODULE_ID, invalidBaseScore))
      .expectError(IllegalArgumentException.class)
      .verify();
  }

  @Test
  void setBonusScores_ValidBonusScores_SetsBaseScore() {
    List<Integer> validBonus = new ArrayList<Integer>();

    validBonus.add(100);
    validBonus.add(50);
    validBonus.add(10);

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithBonusScores = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(mockModule.withBonusScores(validBonus)).thenReturn(mockModuleWithBonusScores);

    when(moduleRepository.save(mockModuleWithBonusScores)).thenReturn(Mono.just(mockModuleWithBonusScores));

    StepVerifier
      .create(moduleService.setBonusScores(TestConstants.TEST_MODULE_ID, validBonus))
      .expectNext(mockModuleWithBonusScores)
      .verifyComplete();
  }

  @Test
  void setBaseScore_NullBonusScore_RemovesBonusScores() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithoutBonusScores = mock(ModuleEntity.class);

    when(moduleRepository.save(mockModuleWithoutBonusScores)).thenReturn(Mono.just(mockModuleWithoutBonusScores));

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(mockModule.withBonusScores(null)).thenReturn(mockModuleWithoutBonusScores);

    StepVerifier
      .create(moduleService.setBonusScores(TestConstants.TEST_MODULE_ID, null))
      .expectNext(mockModuleWithoutBonusScores)
      .verifyComplete();

    verify(moduleRepository).save(mockModuleWithoutBonusScores);
  }

  @Test
  void setStaticFlag_EmptyStaticFlag_ReturnsInvalidFlagException() {
    StepVerifier.create(moduleService.setStaticFlag("id", "")).expectError(InvalidFlagException.class).verify();
  }

  @Test
  void setStaticFlag_NullStaticFlag_ReturnsNullPointerException() {
    StepVerifier
      .create(moduleService.setStaticFlag("id", null))
      .expectErrorMatches(throwable ->
        throwable instanceof NullPointerException && throwable.getMessage().equals("Flag cannot be null")
      )
      .verify();
  }

  @Test
  void setStaticFlag_ValidStaticFlag_SetsFlagToStatic() {
    final String staticFlag = "setStaticFlag_ValidStaticFlag_SetsFlagToStatic";

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithStaticFlag = mock(ModuleEntity.class);
    final ModuleEntity mockModuleWithStaticFlagEnabled = mock(ModuleEntity.class);

    when(moduleRepository.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(mockModule.withFlagStatic(true)).thenReturn(mockModuleWithStaticFlag);
    when(mockModuleWithStaticFlag.withStaticFlag(staticFlag)).thenReturn(mockModuleWithStaticFlagEnabled);

    when(moduleRepository.save(mockModuleWithStaticFlagEnabled)).thenReturn(Mono.just(mockModuleWithStaticFlagEnabled));

    StepVerifier
      .create(moduleService.setStaticFlag(TestConstants.TEST_MODULE_ID, staticFlag))
      .expectNext(mockModuleWithStaticFlagEnabled)
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    moduleService = new ModuleService(moduleRepository, keyService, moduleListRepository);
  }
}
