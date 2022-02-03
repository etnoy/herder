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

import java.time.Clock;
import java.time.LocalDateTime;

import org.owasp.herder.flag.FlagHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class CsrfService {
  private final CsrfAttackRepository csrfAttackRepository;

  private final FlagHandler flagHandler;

  private Clock clock;

  public CsrfService(
      final CsrfAttackRepository csrfAttackRepository, final FlagHandler flagHandler) {
    this.csrfAttackRepository = csrfAttackRepository;
    this.flagHandler = flagHandler;
    resetClock();
  }

  public void resetClock() {
    this.clock = Clock.systemDefaultZone();
  }

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public Mono<Void> attack(final String pseudonym, final String moduleName) {
    return csrfAttackRepository
        .findByPseudonymAndModuleName(pseudonym, moduleName)
        .map(attack -> attack.withFinished(LocalDateTime.now(clock)))
        .flatMap(csrfAttackRepository::save)
        .then(Mono.empty());
  }

  public Mono<String> getPseudonym(final long userId, final String moduleName) {
    return flagHandler.getSaltedHmac(userId, moduleName, "csrfPseudonym");
  }

  public Mono<Boolean> validatePseudonym(final String pseudonym, final String moduleName) {
    return csrfAttackRepository
        .countByPseudonymAndModuleName(pseudonym, moduleName)
        .map(count -> count > 0);
  }

  public Mono<Boolean> validate(final String pseudonym, final String moduleName) {
    return csrfAttackRepository
        .findByPseudonymAndModuleName(pseudonym, moduleName)
        .map(attack -> attack.getFinished() != null)
        .switchIfEmpty(
            csrfAttackRepository
                .save(
                    CsrfAttack.builder()
                        .pseudonym(pseudonym)
                        .started(LocalDateTime.now(clock))
                        .moduleName(moduleName)
                        .build())
                .then(Mono.just(false)));
  }
}
