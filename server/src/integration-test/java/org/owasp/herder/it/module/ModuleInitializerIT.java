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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.InvalidHerderModuleTypeException;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.ModuleTag;
import org.owasp.herder.module.ModuleTag.ModuleTagBuilder;
import org.owasp.herder.module.ModuleTagRepository;
import org.owasp.herder.module.Score;
import org.owasp.herder.module.Tag;
import org.owasp.herder.scoring.ModulePoint;
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

  @Autowired ModuleTagRepository moduleTagRepository;

  @Autowired IntegrationTestUtils integrationTestUtils;

  ModuleInitializer moduleInitializer;

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

  // TODO: test for invalid score configurations

  @Test
  @DisplayName("Can register a Herder module with scores")
  void canRegisterHerderModuleWithScores() {
    final String moduleLocator = "scored-module";

    @HerderModule("Scored module")
    @Locator(moduleLocator)
    @Score(baseScore = 100, goldBonus = 50, silverBonus = 30, bronzeBonus = 10)
    class TestModule implements BaseModule {}

    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    Set<ModulePoint> points = new HashSet<>();
    points.add(ModulePoint.builder().moduleId(moduleId).rank(0L).points(100L).build());
    points.add(ModulePoint.builder().moduleId(moduleId).rank(1L).points(50L).build());
    points.add(ModulePoint.builder().moduleId(moduleId).rank(2L).points(30L).build());
    points.add(ModulePoint.builder().moduleId(moduleId).rank(3L).points(10L).build());

    StepVerifier.create(scoreService.getModuleScores(moduleId).map(point -> point.withId(null)))
        .expectNextMatches(moduleScore -> points.contains(moduleScore))
        .expectNextMatches(moduleScore -> points.contains(moduleScore))
        .expectNextMatches(moduleScore -> points.contains(moduleScore))
        .expectNextMatches(moduleScore -> points.contains(moduleScore))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can register a Herder module")
  void canRegisterHerderModule() {
    final String moduleLocator = "test-module";

    @HerderModule("Test module")
    @Locator(moduleLocator)
    class TestModule implements BaseModule {}

    applicationContext.registerBean(TestModule.class, () -> new TestModule());
    moduleInitializer.initializeModules();
    StepVerifier.create(moduleService.findByLocator(moduleLocator))
        .expectNextMatches(module -> module.getLocator().equals(moduleLocator))
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
    final String moduleLocator = "tags-with-same-name";

    @HerderModule("Tags with same name")
    @Locator(moduleLocator)
    @Tag(name = "topic", value = "testing")
    @Tag(name = "topic", value = "production")
    class MultipleTagsWithSameName implements BaseModule {}

    applicationContext.registerBean(
        MultipleTagsWithSameName.class, () -> new MultipleTagsWithSameName());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    final List<ModuleTag> expectedTags = new ArrayList<>();
    final ModuleTagBuilder moduleTagBuilder = ModuleTag.builder().moduleId(moduleId);
    expectedTags.add(moduleTagBuilder.name("topic").value("testing").build());
    expectedTags.add(moduleTagBuilder.name("topic").value("production").build());

    StepVerifier.create(
            moduleService.findAllTagsByModuleId(moduleId).map(moduleTag -> moduleTag.withId(null)))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a module with multiple tags")
  void canInitializeModuleWithMultipleTags() {
    final String moduleLocator = "multiple-tags";

    @HerderModule("Multiple tags")
    @Tag(name = "topic", value = "testing")
    @Tag(name = "difficulty", value = "beginner")
    @Locator(moduleLocator)
    class MultipleTagsModule implements BaseModule {}

    applicationContext.registerBean(MultipleTagsModule.class, () -> new MultipleTagsModule());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    final List<ModuleTag> expectedTags = new ArrayList<>();
    final ModuleTagBuilder moduleTagBuilder = ModuleTag.builder().moduleId(moduleId);
    expectedTags.add(moduleTagBuilder.name("topic").value("testing").build());
    expectedTags.add(moduleTagBuilder.name("difficulty").value("beginner").build());

    StepVerifier.create(
            moduleService.findAllTagsByModuleId(moduleId).map(moduleTag -> moduleTag.withId(null)))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .expectNextMatches(expectedTag -> expectedTags.contains(expectedTag))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a tagged module")
  void canInitializeTaggedModule() {
    final String moduleLocator = "single-tag";

    @HerderModule("Single Tag Module")
    @Locator(moduleLocator)
    @Tag(name = "topic", value = "testing")
    class SingleTagModule implements BaseModule {}

    applicationContext.registerBean(SingleTagModule.class, () -> new SingleTagModule());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    final List<ModuleTag> expectedTags = new ArrayList<>();
    final ModuleTagBuilder moduleTagBuilder = ModuleTag.builder().moduleId(moduleId);
    expectedTags.add(moduleTagBuilder.name("topic").value("testing").build());

    moduleTagRepository.findAllByModuleId(moduleId).doOnNext(System.out::println);

    StepVerifier.create(
            moduleService.findAllTagsByModuleId(moduleId).map(moduleTag -> moduleTag.withId(null)))
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
