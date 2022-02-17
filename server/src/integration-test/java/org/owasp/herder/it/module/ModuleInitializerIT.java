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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.InvalidHerderModuleTypeException;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.ModuleTag;
import org.owasp.herder.module.Tag;
import org.owasp.herder.scoring.ScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

import reactor.test.StepVerifier;

@DirtiesContext
@DisplayName("ModuleInitializer integration tests")
class ModuleInitializerIT extends BaseIT {
  @Autowired GenericApplicationContext applicationContext;

  @Autowired ModuleService moduleService;

  @Autowired ScoreService scoreService;

  ModuleInitializer moduleInitializer;

  @Autowired IntegrationTestUtils integrationTestUtils;

  Map<String, Object> beans;

  @Test
  @DisplayName("Can throw error if module does not extend BaseModule")
  void canThrowErrorIfHerderModuleIsOfWrongType() {
    @HerderModule("wrong-base")
    class TestModuleWrongBase {}

    applicationContext.registerBean(TestModuleWrongBase.class, () -> new TestModuleWrongBase());
    assertThatThrownBy(() -> moduleInitializer.initializeModules())
        .isInstanceOf(InvalidHerderModuleTypeException.class);
  }

  @Test
  @DisplayName("Can register a Herder module")
  void canRegisterHerderModule() {
    final String moduleName = "test-module";

    @HerderModule(moduleName)
    class TestModule implements BaseModule {}

    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    moduleInitializer.initializeModules();
    StepVerifier.create(moduleService.findByName(moduleName))
        .expectNextMatches(module -> module.getName().equals(moduleName))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can throw error if module names collide")
  void initializeModules_NameCollision_ReturnsError() {
    final String moduleName = "test-module";

    @HerderModule(moduleName)
    class TestModule implements BaseModule {}

    @HerderModule(moduleName)
    class TestModuleNameCollision implements BaseModule {}

    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    applicationContext.registerBean(
        TestModuleNameCollision.class, () -> new TestModuleNameCollision());

    assertThatThrownBy(() -> moduleInitializer.initializeModules())
        .isInstanceOf(DuplicateModuleNameException.class);
  }

  @Test
  @DisplayName("Can initialize a module with tags of same name")
  void canInitializeModuleWithTagsOfSameName() {
    final String moduleName = "tags-with-same-name";

    @HerderModule(moduleName)
    @Tag(name = "topic", value = "testing")
    @Tag(name = "topic", value = "production")
    class MultipleTagsWithSameName implements BaseModule {}

    final List<ModuleTag> expectedTags = new ArrayList<>();
    expectedTags.add(
        ModuleTag.builder().moduleName(moduleName).name("topic").value("testing").build());
    expectedTags.add(
        ModuleTag.builder().moduleName(moduleName).name("topic").value("production").build());

    applicationContext.registerBean(
        MultipleTagsWithSameName.class, () -> new MultipleTagsWithSameName());
    moduleInitializer.initializeModules();
    StepVerifier.create(
            moduleService
                .findAllTagsByModuleName(moduleName)
                .map(moduleTag -> moduleTag.withId(null)))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a module with multiple tags")
  void canInitializeModuleWithMultipleTags() {
    final String moduleName = "multiple-tags";

    @HerderModule(moduleName)
    @Tag(name = "topic", value = "testing")
    @Tag(name = "difficulty", value = "beginner")
    class MultipleTagsModule implements BaseModule {}

    final List<ModuleTag> expectedTags = new ArrayList<>();
    expectedTags.add(
        ModuleTag.builder().moduleName(moduleName).name("topic").value("testing").build());
    expectedTags.add(
        ModuleTag.builder().moduleName(moduleName).name("difficulty").value("beginner").build());

    applicationContext.registerBean(MultipleTagsModule.class, () -> new MultipleTagsModule());
    moduleInitializer.initializeModules();
    StepVerifier.create(
            moduleService
                .findAllTagsByModuleName(moduleName)
                .map(moduleTag -> moduleTag.withId(null)))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a module with tag")
  void canInitializeModuleWithTag() {
    final String moduleName = "single-tag";

    @HerderModule(moduleName)
    @Tag(name = "topic", value = "testing")
    class SingleTagModule implements BaseModule {}

    final List<ModuleTag> expectedTags = new ArrayList<>();
    expectedTags.add(
        ModuleTag.builder().moduleName(moduleName).name("topic").value("testing").build());

    applicationContext.registerBean(SingleTagModule.class, () -> new SingleTagModule());
    moduleInitializer.initializeModules();
    StepVerifier.create(
            moduleService
                .findAllTagsByModuleName(moduleName)
                .map(moduleTag -> moduleTag.withId(null)))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .verifyComplete();
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
