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
package org.owasp.herder.configuration;

import lombok.Generated;

import org.owasp.herder.authentication.AuthenticationManager;
import org.owasp.herder.authentication.SecurityContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Generated
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class FilterChainConfiguration {
  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity serverHttpSecurity,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    return serverHttpSecurity
        //
        .exceptionHandling()
        //
        .authenticationEntryPoint(
            (serverWebExchange, authenticationException) ->
                Mono.fromRunnable(
                    () -> {
                      serverWebExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                      Mono.error(authenticationException);
                    }))
        .accessDeniedHandler(
            (serverWebExchange, authenticationException) ->
                Mono.fromRunnable(
                    () -> serverWebExchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN)))
        .and()
        //
        .csrf()
        .disable()
        //
        .formLogin()
        .disable()
        //
        .httpBasic()
        .disable()
        //
        .authenticationManager(authenticationManager)
        .securityContextRepository(securityContextRepository)
        .authorizeExchange()
        .pathMatchers(HttpMethod.OPTIONS)
        .permitAll()
        .pathMatchers("/api/v1/register")
        .permitAll()
        .pathMatchers(HttpMethod.OPTIONS)
        .permitAll()
        .pathMatchers("/api/v1/login")
        .permitAll()
        .anyExchange()
        .authenticated()
        .and()
        .build();
  }
}
