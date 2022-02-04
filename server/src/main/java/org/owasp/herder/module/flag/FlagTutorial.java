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
package org.owasp.herder.module.flag;

import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreService;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class FlagTutorial extends BaseModule {
  private static final String MODULE_NAME = "flag-tutorial";

  public FlagTutorial(
      final ModuleService moduleService,
      final ScoreService scoreService,
      final FlagHandler flagHandler) {
    super("flag-tutorial", moduleService, scoreService, flagHandler);
  }

  @Override
  public Mono<Void> initialize() {
    return Mono.when(
        getScoreService().setModuleScore(MODULE_NAME, 0, 100),
        getScoreService().setModuleScore(MODULE_NAME, 1, 10),
        getScoreService().setModuleScore(MODULE_NAME, 2, 5),
        getScoreService().setModuleScore(MODULE_NAME, 3, 1));
  }
}
