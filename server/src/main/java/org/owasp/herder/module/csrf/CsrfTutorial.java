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
package org.owasp.herder.module.csrf;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfTutorialResult.CsrfTutorialResultBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class CsrfTutorial extends BaseModule {
  private final CsrfService csrfService;

  private static final String MODULE_NAME = "csrf-tutorial";

  public CsrfTutorial(
      final CsrfService csrfService,
      final ModuleService moduleService,
      final FlagHandler flagHandler) {
    super(MODULE_NAME, moduleService, flagHandler, null);
    this.csrfService = csrfService;
  }

  public Mono<CsrfTutorialResult> getTutorial(final long userId) {

    final Mono<String> pseudonym = csrfService.getPseudonym(userId, MODULE_NAME);

    final Mono<CsrfTutorialResultBuilder> resultWithoutFlag =
        pseudonym.map(p -> CsrfTutorialResult.builder().pseudonym(p));

    final Mono<CsrfTutorialResultBuilder> resultWithFlag =
        resultWithoutFlag.zipWith(getFlag(userId)).map(tuple -> tuple.getT1().flag(tuple.getT2()));

    return pseudonym
        .flatMap(pseudo -> csrfService.validate(pseudo, MODULE_NAME))
        .filter(isActive -> isActive)
        .flatMap(isActive -> resultWithFlag)
        .switchIfEmpty(resultWithoutFlag)
        .map(CsrfTutorialResultBuilder::build);
  }

  public Mono<CsrfTutorialResult> attack(final long userId, final String target) {

    CsrfTutorialResultBuilder csrfTutorialResultBuilder = CsrfTutorialResult.builder();

    log.debug(String.format("User %d is attacking csrf target %s", userId, target));

    return csrfService
        .validatePseudonym(target, getModuleName())
        .flatMap(
            valid -> {
              if (Boolean.TRUE.equals(valid)) {
                return csrfService
                    .getPseudonym(userId, getModuleName())
                    .flatMap(
                        pseudonym -> {
                          if (pseudonym.equals(target)) {
                            return Mono.just(
                                csrfTutorialResultBuilder
                                    .error("You cannot activate yourself")
                                    .build());
                          } else {
                            return csrfService
                                .attack(target, getModuleName())
                                .then(
                                    Mono.just(
                                        csrfTutorialResultBuilder
                                            .message("Thank you for voting")
                                            .build()));
                          }
                        });

              } else {
                return Mono.just(csrfTutorialResultBuilder.error("Unknown target ID").build());
              }
            });
  }
}
