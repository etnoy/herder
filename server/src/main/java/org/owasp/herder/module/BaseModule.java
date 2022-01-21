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

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.owasp.herder.exception.InvalidFlagStateException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Base class for all CTF modules */
@Component
@ToString
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public abstract class BaseModule {
  @Getter @NonNull String moduleName;

  @Getter @NonNull ModuleService moduleService;

  @Getter @NonNull FlagHandler flagHandler;

  @NonNull Mono<Module> module;

  @Getter @NonNull Mono<Void> init;

  protected BaseModule(
      String moduleName, ModuleService moduleService, FlagHandler flagHandler, String staticFlag) {
    this.moduleName = moduleName;
    this.moduleService = moduleService;
    this.flagHandler = flagHandler;
    this.module = moduleService.create(moduleName);
    if (staticFlag == null) {
      this.init = Mono.when(this.module);
    } else {
      this.init = Mono.when(this.module, moduleService.setStaticFlag(moduleName, staticFlag));
    }
  }

  /**
   * Computes the static flag for the given module. Throws {@link InvalidFlagStateException} if the
   * module uses a dynamic flag
   *
   * @return The static flag.
   */
  public Mono<String> getFlag() {
    return module.flatMap(
        m -> {
          if (m.isFlagStatic()) {
            return Mono.just(m.getStaticFlag());
          } else {
            return Mono.error(
                new IllegalArgumentException("Cannot get dynamic flag without providing user id"));
          }
        });
  }

  /**
   * Computes the flag for this module
   *
   * @param userId The currently logged in user id
   * @return The flag
   */
  public Mono<String> getFlag(final long userId) {
    return module.flatMap(
        m -> {
          if (m.isFlagStatic()) {
            return Mono.just(m.getStaticFlag());
          } else {
            return flagHandler.getDynamicFlag(userId, moduleName);
          }
        });
  }
}
