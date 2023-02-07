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
package org.owasp.herder.test.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.configuration.ValidatorConfiguration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

@ExtendWith(MockitoExtension.class)
@DisplayName("ValidatorConfiguration unit tests")
class ValidatorConfigurationTest {

  final ValidatorConfiguration validatorConfiguration = new ValidatorConfiguration();

  @Test
  @DisplayName("Can get the validator factory")
  void validatorFactory_ReturnsLocalValidatorFactoryBean() {
    assertThat(validatorConfiguration.validatorFactory()).isInstanceOf(LocalValidatorFactoryBean.class);
  }

  @Test
  @DisplayName("Can get the method validation post processor")
  void methodValidationPostProcessor_ReturnsMethodValidationPostProcessorBean() {
    assertThat(validatorConfiguration.methodValidationPostProcessor())
      .isInstanceOf(MethodValidationPostProcessor.class);
  }
}
