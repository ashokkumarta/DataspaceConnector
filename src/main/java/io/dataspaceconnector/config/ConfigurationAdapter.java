/*
 * Copyright 2020 Fraunhofer Institute for Software and Systems Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dataspaceconnector.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

/**
 * This class configures admin rights for all backend endpoints behind "/api" using the role
 * defined in {@link MultipleEntryPointsSecurityConfig}.
 */
@Log4j2
@Configuration
@Getter(AccessLevel.PUBLIC)
@Setter(AccessLevel.PRIVATE)
public class ConfigurationAdapter extends WebSecurityConfigurerAdapter {

    /**
     * Whether the h2 console is enabled.
     */
    @Value("${spring.h2.console.enabled}")
    private boolean isH2ConsoleEnabled;

    @Override
    protected final void configure(final HttpSecurity http) throws Exception {
        http
                .sessionManagement()
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                .authorizeRequests()
                .antMatchers("/api/ids/data").anonymous()
                .antMatchers("/").anonymous()
                .antMatchers("/api/**").authenticated()
                .antMatchers("/actuator/**").hasRole("ADMIN")
                .antMatchers("/database/**").hasRole("ADMIN")
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .httpBasic()
                .authenticationEntryPoint(authenticationEntryPoint());
        http.headers().xssProtection();

        if (isH2ConsoleEnabled) {
            http.headers().frameOptions().disable();
            if (log.isWarnEnabled()) {
                log.warn("H2 Console enabled. Disabling frame protection.");
            }
        } else {
            http.headers().frameOptions().deny();
        }
    }

    /**
     * Bean with an entry point for the admin realm.
     *
     * @return The authentication entry point for the admin realm.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        final var entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("admin realm");
        return entryPoint;
    }
}
