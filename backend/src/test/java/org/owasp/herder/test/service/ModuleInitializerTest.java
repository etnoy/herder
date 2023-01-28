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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserRepository;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

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
    moduleCandidates.put(TestConstants.TEST_MODULE_NAME, new TestConstants.TestModule());
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    doReturn(Mono.just(TestConstants.TEST_MODULE_ID)).when(moduleInitializer).initializeModule(any());

    moduleInitializer.initializeModules();

    final ArgumentCaptor<BaseModule> baseModuleArgument = ArgumentCaptor.forClass(BaseModule.class);

    verify(moduleInitializer).initializeModule(baseModuleArgument.capture());
    assertThat(baseModuleArgument.getValue()).isInstanceOf(BaseModule.class);
  }

  @Test
  @DisplayName("Can error when trying to initialize module that does not implement BaseModule")
  void initializeModules_ModuleWithInvalidSuperclass_ThrowsError() {
    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put(TestConstants.TEST_MODULE_NAME, new String());
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can error when module names collide")
  void initializeModules_ModuleNamesCollide_ThrowsError() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    class TestModule implements BaseModule {}

    @HerderModule(TestConstants.TEST_MODULE_NAME)
    class TestModuleNameCollision implements BaseModule {}

    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put(TestConstants.TEST_MODULE_NAME, new TestModule());
    moduleCandidates.put(TestConstants.TEST_MODULE_NAME, new TestModuleNameCollision());

    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(DuplicateModuleNameException.class);
  }

  @Test
  @DisplayName("Can initialize a basic valid module")
  void initializeModule_ModulesToInitialize_CreatesModule() {
    @HerderModule(TestConstants.TEST_MODULE_NAME)
    @Locator(TestConstants.TEST_MODULE_LOCATOR)
    class TestModule implements BaseModule {}

    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put(TestConstants.TEST_MODULE_NAME, new TestModule());
    when(moduleService.existsByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(false));
    when(moduleService.create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));

    moduleInitializer.initializeModule(new TestModule());

    final ArgumentCaptor<String> moduleNameArgument = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> moduleLocatorArgument = ArgumentCaptor.forClass(String.class);

    verify(moduleService).create(moduleNameArgument.capture(), moduleLocatorArgument.capture());
    assertThat(moduleNameArgument.getValue()).isEqualTo(TestConstants.TEST_MODULE_NAME);
    assertThat(moduleLocatorArgument.getValue()).isEqualTo(TestConstants.TEST_MODULE_LOCATOR);
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    moduleInitializer = spy(new ModuleInitializer(applicationContext, moduleService));
    moduleInitializer.setApplicationContext(applicationContext);
  }
}
