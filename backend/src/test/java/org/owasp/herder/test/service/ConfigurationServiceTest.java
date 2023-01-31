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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.configuration.ConfigurationRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.model.Configuration;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigurationService unit tests")
class ConfigurationServiceTest extends BaseTest {

  private ConfigurationService configurationService;

  @Mock
  private ConfigurationRepository configurationRepository;

  @Mock
  private KeyService keyService;

  @Test
  @DisplayName("Can get server key when it exists")
  void getServerKey_KeyExists_ReturnsExistingKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final Configuration mockedConfiguration = mock(Configuration.class);

    when(configurationRepository.findByKey(serverKeyConfigurationKey)).thenReturn(Mono.just(mockedConfiguration));
    when(mockedConfiguration.getValue()).thenReturn(Base64.getEncoder().encodeToString(TestConstants.TEST_BYTE_ARRAY));

    StepVerifier
      .create(configurationService.getServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
        verify(configurationRepository).findByKey(serverKeyConfigurationKey);
        verify(keyService, never()).generateRandomBytes(16);
        verify(configurationRepository, never()).save(any(Configuration.class));
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can generate server key when it does not exist")
  void getServerKey_NoKeyExists_ReturnsNewKey() {
    final String serverKeyConfigurationKey = "serverKey";

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(configurationRepository.findByKey(serverKeyConfigurationKey)).thenReturn(Mono.empty());
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration -> Mono.just(configuration.getArgument(0, Configuration.class)));

    StepVerifier
      .create(configurationService.getServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(TestConstants.TEST_BYTE_ARRAY);

        verify(configurationRepository, atLeast(1)).findByKey(serverKeyConfigurationKey);
        verify(keyService).generateRandomBytes(16);
        verify(configurationRepository).save(any(Configuration.class));
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can refresh server key when it does not exist")
  void refreshServerKey_KeyDoesNotExist_GeneratesNewKey() {
    final String serverKeyConfigurationKey = "serverKey";

    final String encodedNewServerKey = Base64.getEncoder().encodeToString(TestConstants.TEST_BYTE_ARRAY);

    when(configurationRepository.findByKey(serverKeyConfigurationKey)).thenReturn(Mono.empty());
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration -> Mono.just(configuration.getArgument(0, Configuration.class)));

    StepVerifier
      .create(configurationService.refreshServerKey())
      .assertNext(key -> assertThat(key).isEqualTo(TestConstants.TEST_BYTE_ARRAY))
      .verifyComplete();

    ArgumentCaptor<Configuration> argument = ArgumentCaptor.forClass(Configuration.class);
    verify(configurationRepository).save(argument.capture());
    assertThat(argument.getValue().getValue()).isEqualTo(encodedNewServerKey);
  }

  @Test
  @DisplayName("Can refresh server key when it exists")
  void refreshServerKey_KeyExists_GeneratesNewKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final Configuration testConfiguration = Configuration
      .builder()
      .key(serverKeyConfigurationKey)
      .value("oldKey")
      .build();

    final String encodedNewServerKey = Base64.getEncoder().encodeToString(TestConstants.TEST_BYTE_ARRAY);

    when(configurationRepository.findByKey(serverKeyConfigurationKey)).thenReturn(Mono.just(testConfiguration));
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration -> Mono.just(configuration.getArgument(0, Configuration.class)));

    StepVerifier
      .create(configurationService.refreshServerKey())
      .assertNext(key -> assertThat(key).isEqualTo(TestConstants.TEST_BYTE_ARRAY))
      .verifyComplete();

    ArgumentCaptor<Configuration> argument = ArgumentCaptor.forClass(Configuration.class);
    verify(configurationRepository).save(argument.capture());
    assertThat(argument.getValue().getValue()).isEqualTo(encodedNewServerKey);
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    configurationService = new ConfigurationService(configurationRepository, keyService);
  }
}
