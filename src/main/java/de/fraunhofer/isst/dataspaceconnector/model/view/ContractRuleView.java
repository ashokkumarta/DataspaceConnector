package de.fraunhofer.isst.dataspaceconnector.model.view;

import java.time.ZonedDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

@Getter
@Setter
@Relation(collectionRelation = "rules", itemRelation = "rule")
public class ContractRuleView extends RepresentationModel<ContractRuleView> {
    /**
     * The creation date.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private ZonedDateTime creationDate;

    /**
     * The last modification date.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private ZonedDateTime modificationDate;

    private String title;
    private String value;
    private Map<String, String> additional;
}
