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
package org.owasp.herder.service;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.configuration.ConfigurationRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.ConfigurationKeyNotFoundException;
import org.owasp.herder.model.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Validated
@Service
public class ConfigurationService {
  private final ConfigurationRepository configurationRepository;

  private final KeyService keyService;

  private Mono<Configuration> create(
    @NotNull @NotEmpty final String key,
    @NotNull @NotEmpty final String value
  ) {
    log.debug("Creating configuration key " + key + " with value " + value);
    return configurationRepository.save(
      Configuration.builder().key(key).value(value).build()
    );
  }

  private Mono<Boolean> existsByKey(@NotNull @NotEmpty final String key) {
    return configurationRepository
      .findByKey(key)
      .map(u -> true)
      .defaultIfEmpty(false);
  }

  private Mono<String> getByKey(@NotNull @NotEmpty final String key) {
    return configurationRepository
      .findByKey(key)
      .switchIfEmpty(
        Mono.error(
          new ConfigurationKeyNotFoundException(
            "Configuration key " + key + " not found"
          )
        )
      )
      .map(Configuration::getValue);
  }

  public Mono<byte[]> getServerKey() {
    return getByKey("serverKey")
      .map(Base64.getDecoder()::decode)
      .onErrorResume(
        ConfigurationKeyNotFoundException.class,
        notFound -> refreshServerKey()
      );
  }

  public Mono<byte[]> refreshServerKey() {
    final String serverKeyConfigurationKey = "serverKey";
    log.info("Refreshing server key");
    final String newServerKey = Base64
      .getEncoder()
      .encodeToString(keyService.generateRandomBytes(16));
    return existsByKey(serverKeyConfigurationKey)
      .flatMap(
        exists -> {
          if (Boolean.TRUE.equals(exists)) {
            return setValue(serverKeyConfigurationKey, newServerKey);
          } else {
            return create(serverKeyConfigurationKey, newServerKey);
          }
        }
      )
      .map(Configuration::getValue)
      .map(Base64.getDecoder()::decode);
  }

  private Mono<Configuration> setValue(
    @NotNull @NotEmpty final String key,
    @NotNull @NotEmpty final String value
  ) {
    log.debug("Setting configuration key " + key + " to value " + value);
    return Mono
      .just(key)
      .filterWhen(this::existsByKey)
      .switchIfEmpty(
        Mono.error(
          new ConfigurationKeyNotFoundException(
            "Configuration key " + key + " not found"
          )
        )
      )
      .flatMap(configurationRepository::findByKey)
      .flatMap(
        configuration ->
          configurationRepository.save(configuration.withValue(value))
      );
  }
}
