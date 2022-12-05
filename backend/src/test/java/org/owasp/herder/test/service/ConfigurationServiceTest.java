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
  void getServerKey_KeyExists_ReturnsExistingKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final Configuration mockedConfiguration = mock(Configuration.class);

    final byte[] mockedServerKey = {
      -118,
      9,
      -7,
      -35,
      17,
      -116,
      -94,
      0,
      -32,
      -117,
      65,
      -127,
      12,
      82,
      9,
      29,
    };

    when(configurationRepository.findByKey(serverKeyConfigurationKey))
      .thenReturn(Mono.just(mockedConfiguration));
    when(mockedConfiguration.getValue())
      .thenReturn(Base64.getEncoder().encodeToString(mockedServerKey));

    StepVerifier
      .create(configurationService.getServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(mockedServerKey);
        verify(configurationRepository).findByKey(serverKeyConfigurationKey);
        verify(keyService, never()).generateRandomBytes(16);
        verify(configurationRepository, never()).save(any(Configuration.class));
      })
      .verifyComplete();
  }

  @Test
  void getServerKey_NoKeyExists_ReturnsNewKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final byte[] mockedServerKey = {
      -118,
      9,
      -7,
      -35,
      17,
      -116,
      -94,
      0,
      -32,
      -117,
      65,
      -127,
      12,
      82,
      9,
      29,
    };

    when(keyService.generateRandomBytes(16)).thenReturn(mockedServerKey);
    when(configurationRepository.findByKey(serverKeyConfigurationKey))
      .thenReturn(Mono.empty());
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration ->
        Mono.just(configuration.getArgument(0, Configuration.class))
      );

    StepVerifier
      .create(configurationService.getServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(mockedServerKey);

        verify(configurationRepository, atLeast(1))
          .findByKey(serverKeyConfigurationKey);
        verify(keyService).generateRandomBytes(16);
        verify(configurationRepository).save(any(Configuration.class));
      })
      .verifyComplete();
  }

  @Test
  void refreshServerKey_KeyDoesNotExist_GeneratesNewKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final byte[] newServerKey = {
      -118,
      9,
      -7,
      -35,
      17,
      -116,
      -94,
      0,
      -32,
      -117,
      65,
      -127,
      12,
      82,
      9,
      29,
    };
    final String encodedNewServerKey = Base64
      .getEncoder()
      .encodeToString(newServerKey);

    when(configurationRepository.findByKey(serverKeyConfigurationKey))
      .thenReturn(Mono.empty());
    when(keyService.generateRandomBytes(16)).thenReturn(newServerKey);
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration ->
        Mono.just(configuration.getArgument(0, Configuration.class))
      );

    StepVerifier
      .create(configurationService.refreshServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(newServerKey);

        verify(configurationRepository, atLeast(1))
          .findByKey(serverKeyConfigurationKey);
        verify(keyService).generateRandomBytes(16);

        ArgumentCaptor<Configuration> argument = ArgumentCaptor.forClass(
          Configuration.class
        );

        verify(configurationRepository).save(argument.capture());

        assertThat(argument.getValue().getValue())
          .isEqualTo(encodedNewServerKey);
      })
      .verifyComplete();
  }

  @Test
  void refreshServerKey_KeyExists_GeneratesNewKey() {
    final String serverKeyConfigurationKey = "serverKey";
    final Configuration mockedConfiguration = mock(Configuration.class);
    final Configuration mockedConfigurationNewKey = mock(Configuration.class);

    final byte[] newServerKey = {
      -118,
      9,
      -7,
      -35,
      17,
      -116,
      -94,
      0,
      -32,
      -117,
      65,
      -127,
      12,
      82,
      9,
      29,
    };

    final String encodedNewServerKey = Base64
      .getEncoder()
      .encodeToString(newServerKey);

    when(configurationRepository.findByKey(serverKeyConfigurationKey))
      .thenReturn(Mono.just(mockedConfiguration));

    when(mockedConfiguration.withValue(encodedNewServerKey))
      .thenReturn(mockedConfigurationNewKey);
    when(mockedConfigurationNewKey.getValue()).thenReturn(encodedNewServerKey);

    when(keyService.generateRandomBytes(16)).thenReturn(newServerKey);
    when(configurationRepository.save(any(Configuration.class)))
      .thenAnswer(configuration ->
        Mono.just(configuration.getArgument(0, Configuration.class))
      );

    StepVerifier
      .create(configurationService.refreshServerKey())
      .assertNext(serverKey -> {
        assertThat(serverKey).isEqualTo(newServerKey);
        verify(configurationRepository, atLeast(1))
          .findByKey(serverKeyConfigurationKey);
        verify(keyService).generateRandomBytes(16);

        verify(mockedConfiguration).withValue(encodedNewServerKey);

        ArgumentCaptor<Configuration> argument = ArgumentCaptor.forClass(
          Configuration.class
        );

        verify(configurationRepository).save(argument.capture());

        assertThat(argument.getValue()).isEqualTo(mockedConfigurationNewKey);
        assertThat(argument.getValue().getValue())
          .isEqualTo(encodedNewServerKey);
      })
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    configurationService =
      new ConfigurationService(configurationRepository, keyService);
  }
}
