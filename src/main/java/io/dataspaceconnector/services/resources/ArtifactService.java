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
package io.dataspaceconnector.services.resources;

import io.dataspaceconnector.exceptions.PolicyRestrictionException;
import io.dataspaceconnector.exceptions.UnreachableLineException;
import io.dataspaceconnector.model.Artifact;
import io.dataspaceconnector.model.ArtifactDesc;
import io.dataspaceconnector.model.ArtifactFactory;
import io.dataspaceconnector.model.ArtifactImpl;
import io.dataspaceconnector.model.LocalData;
import io.dataspaceconnector.model.QueryInput;
import io.dataspaceconnector.model.RemoteData;
import io.dataspaceconnector.repositories.ArtifactRepository;
import io.dataspaceconnector.repositories.DataRepository;
import io.dataspaceconnector.services.ArtifactRetriever;
import io.dataspaceconnector.services.HttpService;
import io.dataspaceconnector.services.usagecontrol.PolicyVerifier;
import io.dataspaceconnector.services.usagecontrol.VerificationResult;
import io.dataspaceconnector.utils.ErrorMessages;
import io.dataspaceconnector.utils.Utils;
import kotlin.NotImplementedError;
import kotlin.Pair;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the basic logic for artifacts.
 */
@Log4j2
@Service
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.NONE)
public class ArtifactService extends BaseEntityService<Artifact, ArtifactDesc>
        implements RemoteResolver {
    /**
     * Repository for storing data.
     **/
    private final @NonNull DataRepository dataRepo;

    /**
     * Service for http communication.
     **/
    private final @NonNull HttpService httpSvc;

    /**
     * Constructor for ArtifactService.
     *
     * @param dataRepository The data repository.
     * @param httpService    The HTTP service for fetching remote data.
     */
    @Autowired
    public ArtifactService(final @NonNull DataRepository dataRepository,
                           final @NonNull HttpService httpService) {
        super();
        this.dataRepo = dataRepository;
        this.httpSvc = httpService;
    }

    /**
     * Persist the artifact and its data.
     *
     * @param artifact The artifact to persists.
     * @return The persisted artifact.
     */
    @Override
    protected Artifact persist(final Artifact artifact) {
        final var tmp = (ArtifactImpl) artifact;
        if (tmp.getData() != null) {
            if (tmp.getData().getId() == null) {
                // The data element is new, insert
                dataRepo.saveAndFlush(tmp.getData());
            } else {
                // The data element exists already, check if an update is
                // required
                final var storedCopy = dataRepo.getById(tmp.getData().getId());
                if (!storedCopy.equals(tmp.getData())) {
                    dataRepo.saveAndFlush(tmp.getData());
                }
            }

            if (tmp.getData() instanceof LocalData) {
                final var factory = (ArtifactFactory) getFactory();
                factory.updateByteSize(artifact, ((LocalData) tmp.getData()).getValue());
            }
        }

        return super.persist(tmp);
    }

    /**
     * Get the artifacts data. If agreements for this resource exist, all of them will be tried for
     * data access.
     *
     * @param accessVerifier Checks if the data access should be allowed.
     * @param retriever      Retrieves the data from an external source.
     * @param artifactId     The id of the artifact.
     * @param queryInput     The query for the backend.
     * @return The artifacts data.
     * @throws PolicyRestrictionException if the data access has been denied.
     * @throws io.dataspaceconnector.exceptions.ResourceNotFoundException
     *         if the artifact does not exist.
     * @throws IllegalArgumentException   if any of the parameters is null.
     */
    @Transactional
    public InputStream getData(final PolicyVerifier<Artifact> accessVerifier,
                               final ArtifactRetriever retriever, final UUID artifactId,
                               final QueryInput queryInput)
            throws PolicyRestrictionException, IOException {
        // TODO: Parameter Null checks

        /*
         * NOTE: Check if agreements with remoteIds are set for this artifact. If such agreements
         * exist the artifact must be assigned to a requested resource. The data access should
         * now be treated from the perspective of the data consumer. Since no knowledge which
         * agreement applies has been passed we need to query the database for all viable agreements
         * and try accessing the data till one of them returns the data. If none of them returns
         * the data it means all data access has been forbidden. Do not proceed.
         */
        final var agreements =
                ((ArtifactRepository) getRepository()).findRemoteOriginAgreements(artifactId);
        if (agreements.size() > 0) {
            var policyException = new PolicyRestrictionException(ErrorMessages.POLICY_RESTRICTION);
            for (final var agRemoteId : agreements) {
                try {
                    final var info = new RetrievalInformation(agRemoteId, null, queryInput);
                    return getData(accessVerifier, retriever, artifactId, info);
                } catch (PolicyRestrictionException exception) {
                    // Access denied, log it and try the next agreement.
                    if (log.isDebugEnabled()) {
                        log.debug("Tried to access artifact data by trying an agreement. "
                                        + "[artifactId=({}), agreementId=({})]",
                                artifactId, agRemoteId);
                    }

                    policyException = exception;
                }
            }

            // All attempts on accessing data failed. Deny access with the last rejection reason.
            if (log.isDebugEnabled()) {
                log.debug("The requested resource is not owned by this connector."
                        + " Access forbidden. [artifactId=({})]", artifactId);
            }

            throw policyException;
        }

        // The artifact is not assigned to any requested resources. It must be offered if it exists.
        return getDataFromInternalDB((ArtifactImpl) get(artifactId), queryInput);
    }

    /**
     * Get data restricted by a contract. If the data is not available an artifact requests will
     * pull the data.
     *
     * @param accessVerifier Checks if the data access should be allowed.
     * @param retriever      Retrieves the data from an external source.
     * @param artifactId     The id of the artifact.
     * @param information    Information for pulling the data from a remote source.
     * @return The artifact's data.
     * @throws PolicyRestrictionException if the data access has been denied.
     * @throws io.dataspaceconnector.exceptions.ResourceNotFoundException
     *         if the artifact does not exist.
     * @throws IllegalArgumentException   if any of the parameters is null.
     */
    @Transactional
    public InputStream getData(final PolicyVerifier<Artifact> accessVerifier,
                               final ArtifactRetriever retriever, final UUID artifactId,
                               final RetrievalInformation information)
            throws PolicyRestrictionException, IOException {

        // TODO: Parameter Null checks

        // Check the artifact exists and access is granted.
        final var artifact = get(artifactId);
        if (accessVerifier.verify(artifact) == VerificationResult.DENIED) {
            if (log.isInfoEnabled()) {
                log.info("Access denied. [artifactId=({})]", artifactId);
            }

            throw new PolicyRestrictionException(ErrorMessages.POLICY_RESTRICTION);
        }

        // Make sure the data exists and is up to date.
        final var shouldDownload = shouldDownload(artifact, information.getForceDownload());
        if (shouldDownload) {
            /*
                NOTE: Make this not blocking.
             */
            final var dataStream = retriever.retrieve(artifactId,
                    artifact.getRemoteAddress(), information.getTransferContract(),
                    information.getQueryInput());
            final var persistedData = setData(artifactId, dataStream);
            artifact.incrementAccessCounter();
            persist(artifact);
            return persistedData;
        }

        // Artifact exists, access granted, data exists and data up to date.
        return getDataFromInternalDB((ArtifactImpl) artifact, null);
    }

    /**
     * Get the data from the internal database. No policy enforcement is performed here!
     *
     * @param artifact   The artifact which data should be returned.
     * @param queryInput The query for the data backend. May be null.
     * @return The artifact's data.
     * @throws IOException if the data cannot be received.
     */
    private InputStream getDataFromInternalDB(final ArtifactImpl artifact,
                                              final QueryInput queryInput) throws IOException {
        final var data = artifact.getData();

        InputStream rawData;
        if (data instanceof LocalData) {
            rawData = getData((LocalData) data);
        } else if (data instanceof RemoteData) {
            rawData = getData((RemoteData) data, queryInput);
        } else {
            throw new UnreachableLineException("Unknown data type.");
        }

        artifact.incrementAccessCounter();
        persist(artifact);

        return rawData;
    }

    private boolean shouldDownload(final Artifact artifact, final Boolean forceDownload) {
        if (forceDownload == null) {
            // TODO: Add checks if the data is still up to date. This will remove unnecessary
            //  downloads.
            return !isDataPresent() || artifact.isAutomatedDownload();
        } else {
            return forceDownload;
        }
    }

    private boolean isDataPresent() {
        // TODO: Check if the data has been downloaded at least once.
        return false;
    }

    /**
     * Get local data.
     *
     * @param data The data container.
     * @return The stored data.
     */
    private InputStream getData(final LocalData data) {
        return toInputStream(data.getValue());
    }

    /**
     * Get remote data.
     *
     * @param data       The data container.
     * @param queryInput The query for the backend.
     * @return The stored data.
     */
    private InputStream getData(final RemoteData data, final QueryInput queryInput)
            throws IOException {
        try {
            InputStream backendData;
            if (data.getUsername() != null || data.getPassword() != null) {
                backendData = httpSvc.get(data.getAccessUrl(), queryInput,
                                             new Pair<>(data.getUsername(), data.getPassword()))
                                      .getBody();
            } else {
                backendData = httpSvc.get(data.getAccessUrl(), queryInput).getBody();
            }

            return backendData;
        } catch (IOException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Could not connect to data source. [exception=({})]",
                        exception.getMessage(), exception);
            }

            throw new IOException("Could not connect to data source.", exception);
        }
    }

    /**
     * Finds all artifacts referenced in a specific agreement.
     *
     * @param agreementId ID of the agreement
     * @return list of all artifacts referenced in the agreement
     */
    public List<Artifact> getAllByAgreement(final UUID agreementId) {
        Utils.requireNonNull(agreementId, ErrorMessages.ENTITYID_NULL);
        return ((ArtifactRepository) getRepository()).findAllByAgreement(agreementId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UUID> identifyByRemoteId(final URI remoteId) {
        final var repo = (ArtifactRepository) getRepository();
        return repo.identifyByRemoteId(remoteId);
    }

    /**
     * Update an artifacts underlying data.
     *
     * @param artifactId The artifact which should be updated.
     * @param data       The new data.
     * @return The data stored in the artifact.
     * @throws IOException if the data could not be stored.
     */
    @Transactional
    public InputStream setData(final UUID artifactId, final InputStream data) throws IOException {
        final var artifact = get(artifactId);
        final var localData = ((ArtifactImpl) artifact).getData();
        if (localData instanceof LocalData) {
            try {
                /*
                 * NOTE: The service or the factories need to implement some form of patching. But
                 * since this is the only place where a single value is updated its enough to use a
                 * query for this.
                 */

                // Update the internal database and return the new data.
                final var bytes = data.readAllBytes();
                data.close();
                dataRepo.setLocalData(localData.getId(), bytes);
                if (((ArtifactFactory) getFactory()).updateByteSize(artifact, bytes)) {
                    ((ArtifactRepository) getRepository()).setArtifactData(artifactId,
                            artifact.getCheckSum(),
                            artifact.getByteSize());
                }

                return new ByteArrayInputStream(bytes);
            } catch (IOException e) {
                if (log.isErrorEnabled()) {
                    log.error("Failed to store data. [artifactId=({}), exception=({})]",
                            artifactId, e.getMessage(), e);
                }

                throw new IOException("Failed to store data.", e);
            }
        } else {
            // TODO Push data to remote backend. Missing concept.
            throw new NotImplementedError();
        }
    }

    private InputStream toInputStream(final byte[] data) {
        return new ByteArrayInputStream(data);
    }
}
