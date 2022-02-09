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

import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.csrf.CsrfTutorialResult.CsrfTutorialResultBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@HerderModule(name = "csrf-tutorial", baseScore = 100)
public class CsrfTutorial extends BaseModule {
  private final CsrfService csrfService;

  private final FlagHandler flagHandler;

  public Mono<CsrfTutorialResult> getTutorial(final long userId) {
    final Mono<String> pseudonym = csrfService.getPseudonym(userId, getName());

    final Mono<CsrfTutorialResultBuilder> resultWithoutFlag =
        pseudonym.map(p -> CsrfTutorialResult.builder().pseudonym(p));

    final Mono<CsrfTutorialResultBuilder> resultWithFlag =
        resultWithoutFlag
            .zipWith(flagHandler.getDynamicFlag(userId, getName()))
            .map(tuple -> tuple.getT1().flag(tuple.getT2()));

    return pseudonym
        .flatMap(pseudo -> csrfService.validate(pseudo, getName()))
        .filter(isActive -> isActive)
        .flatMap(isActive -> resultWithFlag)
        .switchIfEmpty(resultWithoutFlag)
        .map(CsrfTutorialResultBuilder::build);
  }

  public Mono<CsrfTutorialResult> attack(final long userId, final String target) {

    CsrfTutorialResultBuilder csrfTutorialResultBuilder = CsrfTutorialResult.builder();

    log.debug(String.format("User %d is attacking csrf target %s", userId, target));

    return csrfService
        .validatePseudonym(target, getName())
        .flatMap(
            valid -> {
              if (Boolean.TRUE.equals(valid)) {
                return csrfService
                    .getPseudonym(userId, getName())
                    .flatMap(
                        pseudonym -> {
                          if (pseudonym.equals(target)) {
                            return Mono.just(
                                csrfTutorialResultBuilder
                                    .error("You cannot activate yourself")
                                    .build());
                          } else {
                            return csrfService
                                .attack(target, getName())
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
