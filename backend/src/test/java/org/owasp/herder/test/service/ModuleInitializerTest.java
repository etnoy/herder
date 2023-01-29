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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.guava.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Multimap;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.ModuleInitializationException;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.Score;
import org.owasp.herder.module.Tag;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserRepository;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleInitializer unit tests")
class ModuleInitializerTest extends BaseTest {

  ModuleInitializer moduleInitializer;

  @Mock
  ModuleService moduleService;

  @Mock
  ModuleRepository moduleRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  ApplicationContext applicationContext;

  @HerderModule(TestConstants.TEST_MODULE_NAME)
  @Locator(TestConstants.TEST_MODULE_LOCATOR)
  private static final class TestModule implements BaseModule {}

  @Test
  @DisplayName("Can do nothing when there are no modules to initialize")
  void initializeModules_NoModulesToInitialize_DoesNothing() {
    final Map<String, Object> moduleCandidates = new HashMap<>();
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    assertThatCode(() -> {
        moduleInitializer.initializeModules();
      })
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can initialize a single module")
  void initializeModules_SingleModule_CallsInitializesModule() {
    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put("1", new TestModule());
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    doReturn(Mono.just(TestConstants.TEST_MODULE_ID)).when(moduleInitializer).initializeModule(any());

    moduleInitializer.initializeModules();

    final ArgumentCaptor<BaseModule> baseModuleArgument = ArgumentCaptor.forClass(BaseModule.class);

    verify(moduleInitializer).initializeModule(baseModuleArgument.capture());
    assertThat(baseModuleArgument.getValue()).isInstanceOf(BaseModule.class);
  }

  @Test
  @DisplayName("Can error when trying to initialize module that does not implement BaseModule")
  void initializeModules_ModuleNotImplementingBaseModule_ThrowsError() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    class NotImplementingBaseModule {}

    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put("1", new NotImplementingBaseModule());
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can error when module names collide")
  void initializeModules_ModuleNamesCollide_ThrowsError() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    class NameCollision implements BaseModule {}

    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put("1", new TestModule());
    moduleCandidates.put("2", new NameCollision());

    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    assertThatThrownBy(() -> moduleInitializer.initializeModules())
      .isInstanceOf(DuplicateModuleNameException.class)
      .hasMessageStartingWith("The following modules have colliding names:");
  }

  @Test
  @DisplayName("Can handle a constraint violation in module data")
  void initializeModules_ConstraintViolation_ThrowsError() {
    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put("1", new TestModule());

    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    doThrow(new ConstraintViolationException("Module name invalid", null))
      .when(moduleInitializer)
      .initializeModule(any());

    assertThatThrownBy(() -> moduleInitializer.initializeModules())
      .isInstanceOf(ModuleInitializationException.class)
      .hasMessageContaining("has invalid metadata");
  }

  @Test
  @DisplayName("Can initialize a valid module")
  void initializeModule_ValidModule_CreatesModule() {
    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));
    when(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));

    final TestModule testModule = new TestModule();

    StepVerifier
      .create(moduleInitializer.initializeModule(testModule))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();

    final ArgumentCaptor<String> moduleNameArgument = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> moduleLocatorArgument = ArgumentCaptor.forClass(String.class);

    verify(moduleService).create(moduleNameArgument.capture(), moduleLocatorArgument.capture());
    assertThat(moduleNameArgument.getValue()).isEqualTo(TestConstants.TEST_MODULE_NAME);
    assertThat(moduleLocatorArgument.getValue()).isEqualTo(TestConstants.TEST_MODULE_LOCATOR);
  }

  @Test
  @DisplayName("Can initialize a valid module with base score")
  void initializeModule_ValidModuleWithBaseScore_CreatesModule() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    @Score(baseScore = 100)
    class BaseScoreModule implements BaseModule {}

    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));
    when(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));
    when(moduleService.setBaseScore(TestConstants.TEST_MODULE_ID, 100)).thenReturn(Mono.empty());

    when(moduleService.setBonusScores(eq(TestConstants.TEST_MODULE_ID), any())).thenReturn(Mono.empty());

    final BaseScoreModule testModule = new BaseScoreModule();

    StepVerifier
      .create(moduleInitializer.initializeModule(testModule))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<Integer>> bonusScoreArgument = ArgumentCaptor.forClass(List.class);

    verify(moduleService).setBonusScores(eq(TestConstants.TEST_MODULE_ID), bonusScoreArgument.capture());
    assertThat(bonusScoreArgument.getValue()).containsExactly(0, 0, 0);
  }

  @Test
  @DisplayName("Can initialize a valid module with bonus scores")
  void initializeModule_ValidModuleWithBonusScores_CreatesModule() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    @Score(baseScore = 100, goldBonus = 10, silverBonus = 5, bronzeBonus = 1)
    class BonusScoreModule implements BaseModule {}

    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));
    when(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));
    when(moduleService.setBaseScore(TestConstants.TEST_MODULE_ID, 100)).thenReturn(Mono.empty());

    when(moduleService.setBonusScores(eq(TestConstants.TEST_MODULE_ID), any())).thenReturn(Mono.empty());

    final BonusScoreModule testModule = new BonusScoreModule();

    StepVerifier
      .create(moduleInitializer.initializeModule(testModule))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<Integer>> bonusScoreArgument = ArgumentCaptor.forClass(List.class);

    verify(moduleService).setBonusScores(eq(TestConstants.TEST_MODULE_ID), bonusScoreArgument.capture());
    assertThat(bonusScoreArgument.getValue()).containsExactly(10, 5, 1);
  }

  @Test
  @DisplayName("Can initialize a valid module with tags")
  void initializeModule_ValidModuleWithTags_CreatesModule() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    @Tag(key = "topic", value = "xss")
    class TagModule implements BaseModule {}

    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));
    when(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));

    when(moduleService.setTags(eq(TestConstants.TEST_MODULE_ID), any())).thenReturn(Mono.empty());

    final TagModule testModule = new TagModule();

    StepVerifier
      .create(moduleInitializer.initializeModule(testModule))
      .expectNext(TestConstants.TEST_MODULE_ID)
      .verifyComplete();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Multimap<String, String>> tagArgument = ArgumentCaptor.forClass(Multimap.class);

    verify(moduleService).setTags(eq(TestConstants.TEST_MODULE_ID), tagArgument.capture());
    final Multimap<String, String> tags = tagArgument.getValue();
    assertThat(tags).containsKeys("topic");
    assertThat(tags).containsValues("xss");
  }

  @Test
  @DisplayName("Can error when module is missing @Locator")
  void initializeModule_MissingLocatorAnnotation_ReturnsError() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    class MissingLocator implements BaseModule {}

    final MissingLocator testModule = new MissingLocator();

    assertThatThrownBy(() -> moduleInitializer.initializeModule(testModule))
      .isInstanceOf(ModuleInitializationException.class)
      .hasMessageStartingWith("Missing @Locator on module");
  }

  @Test
  @DisplayName("Can error when module is missing @HerderModule")
  void initializeModule_MissingHerderModuleAnnotation_ReturnsError() {
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    class MissingHederAnnotation implements BaseModule {}

    final MissingHederAnnotation testModule = new MissingHederAnnotation();

    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));

    assertThatThrownBy(() -> moduleInitializer.initializeModule(testModule))
      .isInstanceOf(ModuleInitializationException.class)
      .hasMessageContaining("is missing the @HerderModule annotation");
  }

  @Test
  @DisplayName("Can do nothing when module is already persisted")
  void initializeModule_ModuleAlreadyPersisted_DoesNothing() {
    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(true));

    final TestModule testModule = new TestModule();

    StepVerifier.create(moduleInitializer.initializeModule(testModule)).verifyComplete();

    verify(moduleService, never()).create(any(), any());
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    moduleInitializer = spy(new ModuleInitializer(applicationContext, moduleService));
    moduleInitializer.setApplicationContext(applicationContext);
  }
}
