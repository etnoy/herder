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
package org.owasp.herder.module.csrf;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.flag.FlagHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CsrfService {

  private final CsrfAttackRepository csrfAttackRepository;

  private final FlagHandler flagHandler;

  private final Clock clock;

  public Mono<Void> attack(final String pseudonym, final String moduleName) {
    return csrfAttackRepository
      .findByPseudonymAndModuleLocator(pseudonym, moduleName)
      .map(attack -> attack.withFinished(LocalDateTime.now(clock)))
      .flatMap(csrfAttackRepository::save)
      .then(Mono.empty());
  }

  public Mono<String> getPseudonym(
    final String userId,
    final String moduleLocator
  ) {
    return flagHandler.getSaltedHmac(userId, moduleLocator, "csrfPseudonym");
  }

  public Mono<Boolean> validatePseudonym(
    final String pseudonym,
    final String moduleLocator
  ) {
    return csrfAttackRepository
      .countByPseudonymAndModuleLocator(pseudonym, moduleLocator)
      .map(count -> count > 0);
  }

  public Mono<Boolean> validate(
    final String pseudonym,
    final String moduleLocator
  ) {
    return csrfAttackRepository
      .findByPseudonymAndModuleLocator(pseudonym, moduleLocator)
      .map(attack -> attack.getFinished() != null)
      .switchIfEmpty(
        csrfAttackRepository
          .save(
            CsrfAttack
              .builder()
              .pseudonym(pseudonym)
              .started(LocalDateTime.now(clock))
              .moduleLocator(moduleLocator)
              .build()
          )
          .then(Mono.just(false))
      );
  }
}
