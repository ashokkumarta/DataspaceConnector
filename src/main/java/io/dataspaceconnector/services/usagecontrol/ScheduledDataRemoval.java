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
package io.dataspaceconnector.services.usagecontrol;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.format.DateTimeParseException;

import io.dataspaceconnector.config.ConnectorConfiguration;
import io.dataspaceconnector.config.UsageControlFramework;
import io.dataspaceconnector.exceptions.ResourceNotFoundException;
import io.dataspaceconnector.services.ids.DeserializationService;
import io.dataspaceconnector.services.resources.AgreementService;
import io.dataspaceconnector.services.resources.ArtifactService;
import io.dataspaceconnector.utils.ContractUtils;
import io.dataspaceconnector.utils.RuleUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * This class implements automated policy check.
 */
@EnableScheduling
@Log4j2
@RequiredArgsConstructor
@Service
public class ScheduledDataRemoval {

    /**
     * The delay of the scheduler.
     */
    private static final int FIXED_DELAY = 60_000;

    /**
     * Service for configuring policy settings.
     */
    private final @NonNull ConnectorConfiguration connectorConfig;

    /**
     * Service for ids deserialization.
     */
    private final @NonNull DeserializationService deserializationService;

    /**
     * Service for ids deserialization.
     */
    private final @NonNull AgreementService agreementService;

    /**
     * Service for updating artifacts.
     */
    private final @NonNull ArtifactService artifactService;

    /**
     * Periodically checks agreements for data deletion.
     */
    @Scheduled(fixedDelay = FIXED_DELAY)
    public void schedule() {
        try {
            if (connectorConfig.getUcFramework() == UsageControlFramework.INTERNAL) {
                if (log.isInfoEnabled()) {
                    log.info("Scanning agreements...");
                }
                scanAgreements();
            }
        } catch (IllegalArgumentException | DateTimeParseException | ResourceNotFoundException e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to check policy. [exception=({})]", e.getMessage());
            }
        }
    }

    /**
     * Checks all known agreements for artifacts that have to be deleted.
     *
     * @throws DateTimeParseException    If a date from a policy cannot be parsed.
     * @throws IllegalArgumentException  If the rule could not be deserialized.
     * @throws ResourceNotFoundException If the data could not be deleted.
     */
    private void scanAgreements() throws DateTimeParseException, IllegalArgumentException,
            ResourceNotFoundException {
        for (final var agreement : agreementService.getAll(Pageable.unpaged())) {
            final var value = agreement.getValue();
            final var idsAgreement = deserializationService.getContractAgreement(value);
            final var rules = ContractUtils.extractRulesFromContract(idsAgreement);
            for (final var rule : rules) {
                final var delete = RuleUtils.checkRuleForPostDuties(rule);
                if (delete) {
                    final var target = rule.getTarget();
                    removeDataFromArtifact(target);
                }
            }
        }
    }

    /**
     * Delete data by artifact id.
     *
     * @param target The artifact id.
     * @throws ResourceNotFoundException If the artifact update fails.
     */
    private void removeDataFromArtifact(final URI target) throws ResourceNotFoundException {
        final var artifactId = artifactService.identifyByRemoteId(target);
        if (artifactId.isPresent()) {
            // Update data for artifact.
            try {
                artifactService.setData(artifactId.get(), InputStream.nullInputStream());
                if (log.isDebugEnabled()) {
                    log.debug("Removed data from artifact. [target=({})]", artifactId);
                }
            } catch (IOException e) {
                if (log.isWarnEnabled()) {
                    log.warn("Failed to remove data from artifact. [target=({})]",
                             artifactId);
                }
            }
        }
    }
}
