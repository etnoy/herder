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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
  @DisplayName("Can initialize a valid module")
  void initializeModules_ModulesToInitialize_DoesNothing() {
    final String moduleLocator = "scored-module";

    @HerderModule("Test module")
    @Locator(moduleLocator)
    class TestModule implements BaseModule {}

    final Map<String, Object> moduleCandidates = new HashMap<>();
    moduleCandidates.put("Test Module", new TestModule());
    when(applicationContext.getBeansWithAnnotation(HerderModule.class)).thenReturn(moduleCandidates);
    when(moduleService.existsByLocator(moduleLocator)).thenReturn(Mono.just(false));
    when(moduleService.create("Test module", moduleLocator)).thenReturn(Mono.just(TestConstants.TEST_MODULE_ID));
    when(moduleService.setTags(eq(TestConstants.TEST_MODULE_ID), any())).thenReturn(Mono.empty());

    moduleInitializer.initializeModules();

    final ArgumentCaptor<String> moduleNameArgument = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<String> moduleLocatorArgument = ArgumentCaptor.forClass(String.class);

    verify(moduleService).create(moduleNameArgument.capture(), moduleLocatorArgument.capture());
    assertThat(moduleNameArgument.getValue()).isEqualTo("Test module");
    assertThat(moduleLocatorArgument.getValue()).isEqualTo(moduleLocator);
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    moduleInitializer = new ModuleInitializer(applicationContext, moduleService);
    moduleInitializer.setApplicationContext(applicationContext);
  }
}
