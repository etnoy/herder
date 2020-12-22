/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
import org.owasp.herder.authentication.ControllerAuthentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/module/csrf-tutorial")
public class CsrfTutorialController {
  private final CsrfTutorial csrfTutorial;

  private final ControllerAuthentication controllerAuthentication;

  @GetMapping(path = "/")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Mono<CsrfTutorialResult> tutorial() {
    return controllerAuthentication.getUserId().flatMap(csrfTutorial::getTutorial);
  }

  @GetMapping(value = {"/activate/{pseudonym}"})
  @PreAuthorize("hasRole('ROLE_USER')")
  public Mono<CsrfTutorialResult> attack(@PathVariable(value = "pseudonym") String pseudonym) {
    return controllerAuthentication
        .getUserId()
        .flatMap(userId -> csrfTutorial.attack(userId, pseudonym));
  }
}
