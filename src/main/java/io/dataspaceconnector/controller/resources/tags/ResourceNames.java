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
package io.dataspaceconnector.controller.resources.tags;

/**
 * The names of tags for resources.
 */
public final class ResourceNames {
    /**
     * Tag name for catalogs.
     */
    public static final String CATALOGS = "Catalogs";

    /**
     * Tag name for rules.
     */
    public static final String RULES = "Rules";

    /**
     * Tag name for representations.
     */
    public static final String REPRESENTATIONS = "Representations";

    /**
     * Tag name for contracts.
     */
    public static final String CONTRACTS = "Contracts";

    /**
     * Tag name for offered resources.
     */
    public static final String OFFERS = "Offered Resources";

    /**
     * Tag name for requested resources.
     */
    public static final String REQUESTS = "Requested Resources";

    /**
     * Tag name for agreements.
     */
    public static final String AGREEMENTS = "Agreements";

    /**
     * Tag name for artifacts.
     */
    public static final String ARTIFACTS = "Artifacts";

    private ResourceNames() {
        // Nothing to do here.
    }
}
