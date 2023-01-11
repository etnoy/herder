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
package org.owasp.herder.it.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.guava.api.Assertions.assertThat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.Score;
import org.owasp.herder.module.Tag;
import org.owasp.herder.scoring.ScoreboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import reactor.test.StepVerifier;

@DirtiesContext
@DisplayName("ModuleInitializer integration tests")
class ModuleInitializerIT extends BaseIT {

  @Autowired
  GenericApplicationContext applicationContext;

  @Autowired
  ModuleService moduleService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  ModuleInitializer moduleInitializer;

  Map<String, Object> beans;

  @Test
  @DisplayName("Can throw error if module does not implement any interface")
  void canThrowErrorIfHerderModuleDoesNotImplementAnyInterface() {
    @HerderModule("no-interface")
    class TestModuleNoSuperClass {}

    applicationContext.registerBean(TestModuleNoSuperClass.class, () -> new TestModuleNoSuperClass());
    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can throw error if module does not implement BaseModule")
  void canThrowErrorIfHerderModuleDoesNotImplementBaseModule() {
    interface NotABaseModule {}

    @HerderModule("wrong-interface")
    class TestModuleWrongBase implements NotABaseModule {}

    applicationContext.registerBean(TestModuleWrongBase.class, () -> new TestModuleWrongBase());
    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  // TODO: test for invalid score configurations

  @Test
  @DisplayName("Can throw error when a Herder module registers an invalid base score")
  void canThrowErrorIfHerderModuleHasInvalidBaseScore() {
    final String moduleLocator = "scored-module";

    @HerderModule("Invalid base score module")
    @Locator(moduleLocator)
    @Score(baseScore = -1)
    class TestModuleWithInvalidBaseScore implements BaseModule {}

    applicationContext.registerBean(TestModuleWithInvalidBaseScore.class, () -> new TestModuleWithInvalidBaseScore());

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can throw error when a Herder module has an invalid gold bonus")
  void canThrowErrorIfHerderModuleHasInvalidGoldBonus() {
    final String moduleLocator = "scored-module";

    @HerderModule("Invalid gold bonus module")
    @Locator(moduleLocator)
    @Score(baseScore = 100, goldBonus = -1)
    class TestModuleWithInvalidGoldBonus implements BaseModule {}

    applicationContext.registerBean(TestModuleWithInvalidGoldBonus.class, () -> new TestModuleWithInvalidGoldBonus());

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can throw error when a Herder module has an invalid silver bonus")
  void canThrowErrorIfHerderModuleHasInvalidSilverBonus() {
    final String moduleLocator = "scored-module";

    @HerderModule("Invalid silver bonus module")
    @Locator(moduleLocator)
    @Score(baseScore = 100, silverBonus = -1)
    class TestModuleWithInvalidSilverBonus implements BaseModule {}

    applicationContext.registerBean(
      TestModuleWithInvalidSilverBonus.class,
      () -> new TestModuleWithInvalidSilverBonus()
    );

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can throw error when a Herder module has an invalid bronze bonus")
  void canThrowErrorIfHerderModuleHasInvalidBronzeBonus() {
    final String moduleLocator = "scored-module";

    @HerderModule("Invalid bronze bonus module")
    @Locator(moduleLocator)
    @Score(baseScore = 100, bronzeBonus = -1)
    class TestModuleWithInvalidBronzeBonus implements BaseModule {}

    applicationContext.registerBean(
      TestModuleWithInvalidBronzeBonus.class,
      () -> new TestModuleWithInvalidBronzeBonus()
    );

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Can register a Herder module with scores")
  void canRegisterHerderModuleWithScores() {
    final String moduleLocator = "scored-module";

    @HerderModule("Scored module")
    @Locator(moduleLocator)
    @Score(baseScore = 100, goldBonus = 50, silverBonus = 30, bronzeBonus = 10)
    class TestModuleWithScores implements BaseModule {}

    applicationContext.registerBean(TestModuleWithScores.class, () -> new TestModuleWithScores());
    moduleInitializer.initializeModules();

    final long baseScore = 100L;

    final ArrayList<Integer> bonusScores = new ArrayList<>();
    bonusScores.add(50);
    bonusScores.add(30);
    bonusScores.add(10);

    StepVerifier
      .create(moduleService.findByLocator(moduleLocator))
      .assertNext(module -> {
        assertThat(module.getBaseScore()).isEqualTo(baseScore);
        assertThat(module.getBonusScores()).isEqualTo(bonusScores);
      })
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
    StepVerifier
      .create(moduleService.findByLocator(moduleLocator))
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
    applicationContext.registerBean(TestModuleNameCollision.class, () -> new TestModuleNameCollision());

    assertThatThrownBy(() -> moduleInitializer.initializeModules()).isInstanceOf(DuplicateModuleNameException.class);
  }

  @Test
  @DisplayName("Can initialize a module with multiple tags with same key")
  void canInitializeModuleWithTagsOfSameName() {
    final String moduleLocator = "tags-with-same-name";

    @HerderModule("Tags with same name")
    @Locator(moduleLocator)
    @Tag(key = "topic", value = "testing")
    @Tag(key = "topic", value = "production")
    class MultipleTagsWithSameName implements BaseModule {}

    applicationContext.registerBean(MultipleTagsWithSameName.class, () -> new MultipleTagsWithSameName());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();
    Multimap<String, String> expectedTags = ArrayListMultimap.create();

    expectedTags.put("topic", "testing");
    expectedTags.put("topic", "production");

    StepVerifier
      .create(moduleService.getById(moduleId))
      .assertNext(module -> {
        assertThat(module.getTags()).hasSameEntriesAs(expectedTags);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a module with multiple tags")
  void canInitializeModuleWithMultipleTags() {
    final String moduleLocator = "multiple-tags";

    @HerderModule("Multiple tags")
    @Tag(key = "topic", value = "testing")
    @Tag(key = "difficulty", value = "beginner")
    @Locator(moduleLocator)
    class MultipleTagsModule implements BaseModule {}

    applicationContext.registerBean(MultipleTagsModule.class, () -> new MultipleTagsModule());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    Multimap<String, String> expectedTags = ArrayListMultimap.create();

    expectedTags.put("topic", "testing");
    expectedTags.put("difficulty", "beginner");

    StepVerifier
      .create(moduleService.getById(moduleId))
      .assertNext(module -> {
        assertThat(module.getTags()).isEqualTo(expectedTags);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can initialize a tagged module")
  void canInitializeTaggedModule() {
    final String moduleLocator = "single-tag";

    @HerderModule("Single Tag Module")
    @Locator(moduleLocator)
    @Tag(key = "topic", value = "testing")
    class SingleTagModule implements BaseModule {}

    applicationContext.registerBean(SingleTagModule.class, () -> new SingleTagModule());
    moduleInitializer.initializeModules();

    final String moduleId = moduleService.findByLocator(moduleLocator).block().getId();

    Multimap<String, String> expectedTags = ArrayListMultimap.create();

    expectedTags.put("topic", "testing");

    StepVerifier
      .create(moduleService.getById(moduleId))
      .assertNext(module -> {
        assertThat(module.getTags()).isEqualTo(expectedTags);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can handle zero modules")
  void initializeModules_NoModules_ModuleInitialized() {
    moduleInitializer.initializeModules();
    StepVerifier.create(moduleService.findAll()).verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    // Remove all other herder modules
    for (final String beanName : applicationContext.getBeansWithAnnotation(HerderModule.class).keySet()) {
      applicationContext.removeBeanDefinition(beanName);
    }

    // Set up the system under test
    moduleInitializer = new ModuleInitializer(applicationContext, moduleService);
  }
}
