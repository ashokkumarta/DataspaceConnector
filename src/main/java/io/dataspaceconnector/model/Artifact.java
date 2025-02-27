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
package io.dataspaceconnector.model;

import java.net.URI;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import io.dataspaceconnector.model.utils.UriConverter;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import static io.dataspaceconnector.model.config.DatabaseConstants.URI_COLUMN_LENGTH;

/**
 * An artifact stores and encapsulates data.
 */
@Inheritance
@Entity
@Table(name = "artifact")
@SQLDelete(sql = "UPDATE artifact SET deleted=true WHERE id=?")
@Where(clause = "deleted = false")
@Getter
@Setter(AccessLevel.PACKAGE)
@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
public abstract class Artifact extends AbstractEntity {

    /**
     * Serial version uid.
     **/
    private static final long serialVersionUID = 1L;

    /**
     * The artifact id on provider side.
     */
    @Convert(converter = UriConverter.class)
    @Column(length = URI_COLUMN_LENGTH)
    private URI remoteId;

    /**
     * The provider's address for artifact request messages.
     */
    @Convert(converter = UriConverter.class)
    @Column(length = URI_COLUMN_LENGTH)
    private URI remoteAddress;

    /**
     * The title of the catalog.
     **/
    private String title;

    /**
     * The counter of how often the underlying data has been accessed.
     */
    private long numAccessed;

    /**
     * Indicates whether the artifact should be downloaded automatically.
     */
    private boolean automatedDownload;

    /**
     * The byte size of the artifact.
     */
    private long byteSize;

    /**
     * The CRC32C CheckSum of the artifact.
     */
    private long checkSum;

    /**
     * The representations in which this artifact is used.
     */
    @ManyToMany(mappedBy = "artifacts")
    private List<Representation> representations;

    /**
     * The agreements that refer to this artifact.
     */
    @ManyToMany(mappedBy = "artifacts")
    private List<Agreement> agreements;

    /**
     * Increment the data access counter.
     */
    public void incrementAccessCounter() {
        numAccessed += 1;
    }
}
