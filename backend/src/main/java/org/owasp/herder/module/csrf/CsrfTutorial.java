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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.HerderModule;
import org.owasp.herder.module.Locator;
import org.owasp.herder.module.Score;
import org.owasp.herder.module.Tag;
import org.owasp.herder.module.csrf.CsrfTutorialResult.CsrfTutorialResultBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@HerderModule("CSRF Tutorial")
@Locator("csrf-tutorial")
@Tag(key = "topic", value = "csrf")
@Score(baseScore = 100, goldBonus = 20, silverBonus = 10, bronzeBonus = 5)
public class CsrfTutorial implements BaseModule {
  private final CsrfService csrfService;

  private final FlagHandler flagHandler;

  public Mono<CsrfTutorialResult> getTutorial(final String userId) {
    final Mono<String> pseudonymMono = csrfService.getPseudonym(
      userId,
      getLocator()
    );

    final Mono<CsrfTutorialResultBuilder> resultWithoutFlag = pseudonymMono.map(
      p -> CsrfTutorialResult.builder().pseudonym(p)
    );

    final Mono<CsrfTutorialResultBuilder> resultWithFlag = resultWithoutFlag
      .zipWith(flagHandler.getDynamicFlag(userId, getLocator()))
      .map(tuple -> tuple.getT1().flag(tuple.getT2()));

    return pseudonymMono
      .flatMap(pseudonym -> csrfService.validate(pseudonym, getLocator()))
      .filter(isActive -> isActive)
      .flatMap(isActive -> resultWithFlag)
      .switchIfEmpty(resultWithoutFlag)
      .map(CsrfTutorialResultBuilder::build);
  }

  public Mono<CsrfTutorialResult> attack(
    final String userId,
    final String target
  ) {
    CsrfTutorialResultBuilder csrfTutorialResultBuilder = CsrfTutorialResult.builder();

    log.debug(
      String.format("User %s is attacking csrf target %s", userId, target)
    );

    return csrfService
      .validatePseudonym(target, getLocator())
      .flatMap(
        valid -> {
          if (Boolean.TRUE.equals(valid)) {
            return csrfService
              .getPseudonym(userId, getLocator())
              .flatMap(
                pseudonym -> {
                  if (pseudonym.equals(target)) {
                    return Mono.just(
                      csrfTutorialResultBuilder
                        .error("You cannot activate yourself")
                        .build()
                    );
                  } else {
                    return csrfService
                      .attack(target, getLocator())
                      .then(
                        Mono.just(
                          csrfTutorialResultBuilder
                            .message("Thank you for voting")
                            .build()
                        )
                      );
                  }
                }
              );
          } else {
            return Mono.just(
              csrfTutorialResultBuilder.error("Unknown target ID").build()
            );
          }
        }
      );
  }
}
