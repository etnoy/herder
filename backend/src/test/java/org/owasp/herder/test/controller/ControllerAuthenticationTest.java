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
package org.owasp.herder.test.controller;

import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.NotAuthenticatedException;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.ReactorContextTestExecutionListener;
import org.springframework.test.context.TestExecutionListener;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("ControllerAuthentication unit tests")
class ControllerAuthenticationTest extends BaseTest {

  private ControllerAuthentication controllerAuthentication;

  @Mock
  private Authentication authentication;

  private final TestExecutionListener reactorContextTestExecutionListener = new ReactorContextTestExecutionListener();

  @BeforeEach
  void authenticate() throws Exception {
    controllerAuthentication = new ControllerAuthentication();
    TestSecurityContextHolder.setAuthentication(authentication);
    reactorContextTestExecutionListener.beforeTestMethod(null);
  }

  @Test
  @DisplayName("Can get authenticated user id")
  void getUserId_UserAuthenticated_ReturnsUserId() {
    when(authentication.getPrincipal()).thenReturn(TestConstants.TEST_USER_ID);
    StepVerifier.create(controllerAuthentication.getUserId()).expectNext(TestConstants.TEST_USER_ID).verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting user id if not authenticated")
  void getUserId_UserNotAuthenticated_ReturnsNotAuthenticatedException() {
    StepVerifier.create(controllerAuthentication.getUserId()).expectError(NotAuthenticatedException.class).verify();
  }
}
