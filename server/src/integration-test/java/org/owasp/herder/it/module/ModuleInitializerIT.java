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
package org.owasp.herder.it.module;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.InvalidHerderModuleTypeException;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {"application.runner.enabled=false"})
@AutoConfigureWebTestClient
@DirtiesContext
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("ModuleInitializer integration tests")
class ModuleInitializerIT extends BaseIT {
  @Autowired GenericApplicationContext applicationContext;

  @Autowired ModuleService moduleService;

  @Autowired ScoreService scoreService;

  ModuleInitializer moduleInitializer;

  @Autowired IntegrationTestUtils integrationTestUtils;

  Map<String, Object> beans;

  @RequiredArgsConstructor
  @HerderModule(name = "test-module")
  public class TestModule extends BaseModule {}

  @RequiredArgsConstructor
  @HerderModule(name = "test-module")
  public class TestModuleNameCollision extends BaseModule {}

  @RequiredArgsConstructor
  @HerderModule(name = "wrong-base")
  public class TestModuleWrongBase {}

  @Test
  @DisplayName("Can throw error if module does not extend BaseModule")
  void initializeModules_WrongType_ReturnsError() {
    applicationContext.registerBean(TestModuleWrongBase.class, () -> new TestModuleWrongBase());
    assertThatThrownBy(() -> moduleInitializer.initializeModules())
        .isInstanceOf(InvalidHerderModuleTypeException.class);
  }

  @Test
  @DisplayName("Can register a Herder module")
  void initializeModules_OneModule_ModuleInitialized() {
    final String moduleName = "test-module";

    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    moduleInitializer.initializeModules();
    StepVerifier.create(moduleService.findByName(moduleName))
        .expectNextMatches(module -> module.getName().equals(moduleName))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can throw error if module names collide")
  void initializeModules_NameCollision_ReturnsError() {
    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    applicationContext.registerBean(
        TestModuleNameCollision.class, () -> new TestModuleNameCollision());

    assertThatThrownBy(() -> moduleInitializer.initializeModules())
        .isInstanceOf(DuplicateModuleNameException.class);
  }

  @Test
  @DisplayName("Can handle zero modules")
  void initializeModules_NoModules_ModuleInitialized() {
    moduleInitializer.initializeModules();
    StepVerifier.create(moduleService.findAll()).verifyComplete();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    // Remove all other herder modules
    for (final String beanName :
        applicationContext.getBeansWithAnnotation(HerderModule.class).keySet()) {
      applicationContext.removeBeanDefinition(beanName);
    }

    // Set up the system under test
    moduleInitializer = new ModuleInitializer(applicationContext, moduleService, scoreService);
  }
}
