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
package org.owasp.herder.module;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.ModuleInitializationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@ConditionalOnProperty(prefix = "application.runner", value = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@AllArgsConstructor
@Service
public final class ModuleInitializer implements ApplicationContextAware {

  private ApplicationContext applicationContext;

  private final ModuleService moduleService;

  @PostConstruct
  public void initializeModules() {
    log.debug("Initializing modules");

    List<BaseModule> modules = new ArrayList<>();
    Set<String> uniqueNames = new HashSet<>();
    List<BaseModule> duplicateModuleNamess = new ArrayList<>();

    // Find all beans annotated with @HerderModule
    for (final Object moduleCandidate : applicationContext.getBeansWithAnnotation(HerderModule.class).values()) {
      if (moduleCandidate instanceof BaseModule baseModule) {
        final String moduleName = baseModule.getName();
        modules.add(baseModule);
        if (!uniqueNames.add(moduleName)) {
          // This module name is a duplicate, which is disallowed
          duplicateModuleNamess.add(baseModule);
        }
      } else {
        throw new IllegalArgumentException(
          "Herder Module " + moduleCandidate.toString() + " does not implement BaseModule"
        );
      }
    }

    if (!duplicateModuleNamess.isEmpty()) {
      throw new DuplicateModuleNameException(
        "The following modules have colliding names: " + duplicateModuleNamess.toString()
      );
    }

    for (final BaseModule module : modules) {
      try {
        // Initialize the module
        initializeModule(module).block();
      } catch (ConstraintViolationException e) {
        throw new ModuleInitializationException(String.format("Module \"%s\" has invalid metadata", module), e);
      }
    }
  }

  public Mono<String> initializeModule(final BaseModule module) {
    log.trace("About to initialize module  " + module);

    final HerderModule herderModuleAnnotation = module.getClass().getAnnotation(HerderModule.class);
    final Score scoreAnnotation = module.getClass().getAnnotation(Score.class);
    final Locator locatorAnnotation = module.getClass().getAnnotation(Locator.class);

    if (locatorAnnotation == null) {
      throw new ModuleInitializationException(String.format("Missing @Locator on module \"%s\"", module));
    }

    final String moduleLocator = locatorAnnotation.value();

    if (Boolean.TRUE.equals(moduleService.existsByLocator(moduleLocator).block())) {
      // If the module already exists in the database, do nothing.
      // This case can happen on, for instance, application restart
      return Mono.empty();
    }

    if (herderModuleAnnotation == null) {
      throw new ModuleInitializationException(
        String.format("Module \"%s\" is missing the @HerderModule annotation", module)
      );
    }

    // Find the module name declared in the annotation
    final String moduleName = herderModuleAnnotation.value();

    log.debug("Initializing module " + moduleName);

    // Find all tag annotations
    final Set<Tag> tagAnnotations = Set.of(module.getClass().getAnnotationsByType(Tag.class));

    return moduleService
      // Persist the module
      .create(moduleName, moduleLocator)
      .flatMap(moduleId -> {
        Mono<Void> scoreMono = Mono.empty();
        // Persist the default scores (if any)
        if (scoreAnnotation != null) {
          final ArrayList<Integer> bonusScores = new ArrayList<>();

          // These bonus scores will be zero if missing
          bonusScores.add(scoreAnnotation.goldBonus());
          bonusScores.add(scoreAnnotation.silverBonus());
          bonusScores.add(scoreAnnotation.bronzeBonus());

          scoreMono =
            moduleService
              .setBaseScore(moduleId, scoreAnnotation.baseScore())
              .then(moduleService.setBonusScores(moduleId, bonusScores))
              .then();
        }

        Mono<Void> tagMono = Mono.empty();
        if (!tagAnnotations.isEmpty()) {
          final Multimap<String, String> tags = ArrayListMultimap.create();
          final Iterator<Tag> tagIterator = tagAnnotations.iterator();
          while (tagIterator.hasNext()) {
            final Tag currentTag = tagIterator.next();
            tags.put(currentTag.key(), currentTag.value());
          }
          tagMono = moduleService.setTags(moduleId, tags);
        }

        return scoreMono.then(tagMono).then(Mono.just(moduleId));
      });
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }
}
