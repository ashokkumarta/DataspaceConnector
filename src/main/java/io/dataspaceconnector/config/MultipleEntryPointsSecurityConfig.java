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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * This class creates an admin role for spring basic security setup used in {@link
 * ConfigurationAdapter}.
 */
@Configuration
@EnableWebSecurity
@Getter(AccessLevel.NONE)
@Setter(AccessLevel.NONE)
public class MultipleEntryPointsSecurityConfig {

    /**
     * Username defined in application.properties.
     */
    @Value("${spring.security.user.name}")
    private String username;

    /**
     * Password defined in application.properties.
     */
    @Value("${spring.security.user.password}")
    private String password;

    /**
     * Bean setting up an default admin.
     *
     * @return The password encoder.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        final var manager = new InMemoryUserDetailsManager();
        manager.createUser(User
                .withUsername(username)
                .password(encoder().encode(password))
                .roles("ADMIN").build());
        return manager;
    }

    /**
     * Bean providing a password encoder.
     *
     * @return The password encoder.
     */
    @Bean
    public PasswordEncoder encoder() {
        return new BCryptPasswordEncoder();
    }
}
