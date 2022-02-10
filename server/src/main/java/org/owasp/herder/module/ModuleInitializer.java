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
package org.owasp.herder.module;

import javax.annotation.PostConstruct;

import org.owasp.herder.scoring.ScoreService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@ConditionalOnProperty(
    prefix = "application.runner",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Slf4j
@AllArgsConstructor
@Service
public final class ModuleInitializer implements ApplicationContextAware {
  private ApplicationContext applicationContext;

  private final ModuleService moduleService;

  private final ScoreService scoreService;

  @PostConstruct
  private void initializeModules() {
    log.debug("Initializing modules");
    for (final Object module :
        applicationContext.getBeansWithAnnotation(HerderModule.class).values()) {
      // TODO: Classcastexception handling
      BaseModule baseModule = (BaseModule) module;

      initializeModule(baseModule).block();
    }
  }

  public Mono<Void> initializeModule(final BaseModule module) {
    final HerderModule moduleAnnotations = module.getClass().getAnnotation(HerderModule.class);
    final String moduleName = moduleAnnotations.name();

    log.debug("Initializing module " + moduleName);
    if (Boolean.TRUE.equals(moduleService.existsByName(moduleName).block())) {
      return Mono.empty();
    } else {
      return moduleService
          .create(moduleName)
          .then(scoreService.setModuleScore(moduleName, 0, moduleAnnotations.baseScore()))
          .then(scoreService.setModuleScore(moduleName, 1, moduleAnnotations.goldBonus()))
          .then(scoreService.setModuleScore(moduleName, 2, moduleAnnotations.silverBonus()))
          .then(scoreService.setModuleScore(moduleName, 3, moduleAnnotations.bronzeBonus()))
          .then();
    }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }
}
