package de.fraunhofer.isst.dataspaceconnector.utils;

import de.fraunhofer.iais.eis.Artifact;
import de.fraunhofer.iais.eis.BaseConnector;
import de.fraunhofer.iais.eis.Connector;
import de.fraunhofer.iais.eis.ContractRequest;
import de.fraunhofer.iais.eis.Representation;
import de.fraunhofer.iais.eis.Resource;
import de.fraunhofer.iais.eis.util.ConstraintViolationException;
import de.fraunhofer.isst.dataspaceconnector.exceptions.ConnectorConfigurationException;
import de.fraunhofer.isst.ids.framework.configuration.ConfigurationContainer;
import de.fraunhofer.isst.ids.framework.configuration.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * This class provides methods to map local connector models to IDS Information Model objects.
 */
@Service
public class IdsUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdsUtils.class);

    private final ConfigurationContainer configurationContainer;
    private final SerializerProvider serializerProvider;

    /**
     * Constructor for IdsUtils.
     *
     * @throws IllegalArgumentException if any of the parameters is null.
     */
    @Autowired
    public IdsUtils(ConfigurationContainer configurationContainer,
        SerializerProvider serializerProvider) throws IllegalArgumentException {
        if (configurationContainer == null)
            throw new IllegalArgumentException("The ConfigurationContainer cannot be null.");

        if (serializerProvider == null)
            throw new IllegalArgumentException("The SerializerProvider cannot be null.");

        this.configurationContainer = configurationContainer;
        this.serializerProvider = serializerProvider;
    }

    /**
     * Returns the current IDS base connector object from the application context.
     *
     * @return The {@link de.fraunhofer.iais.eis.Connector} object from the IDS Framework.
     * @throws ConnectorConfigurationException If the connector was not found.
     */
    public Connector getConnector() throws ConnectorConfigurationException {
        final var connector = configurationContainer.getConnector();
        if (connector == null) {
            // The connector is needed for every answer and cannot be null
            throw new ConnectorConfigurationException("No connector configured.");
        }

        return connector;
    }

    /**
     * Gets the default language, which is the first set language of the connector.
     *
     * @return the default language of the connector.
     * @throws ConnectorConfigurationException if the connector is null or no language is
     *                                         configured.
     */
    private String getDefaultLanguage() throws ConnectorConfigurationException {
        try {
            return getLanguage(0);
        } catch (IndexOutOfBoundsException exception) {
            throw new ConnectorConfigurationException("No default language has been set.");
        }
    }

    /***
     * Gets a language set for the connector from the application context.
     *
     * @param index index of the language.
     * @return the language at the passed index.
     * @throws ConnectorConfigurationException if the connector is null or no language is set.
     * @throws IndexOutOfBoundsException if no language could be found at the passed index.
     */
    @SuppressWarnings("SameParameterValue")
    private String getLanguage(int index)
        throws ConnectorConfigurationException, IndexOutOfBoundsException {
        try {
            final var label = configurationContainer.getConnector().getLabel();
            if (label.size() == 0) {
                throw new ConnectorConfigurationException("No language has been set.");
            }

            final var language = label.get(index).getLanguage();

            if (language.isEmpty()) {
                throw new ConnectorConfigurationException("No language has been set.");
            }

            return language;
        } catch (NullPointerException exception) {
            throw new ConnectorConfigurationException("The connector language configuration could" +
                " not be received.", exception);
        }
    }

    /**
     * Get rdf string from instance of type {@link BaseConnector}.
     *
     * @param baseConnector The ids connector.
     * @return The ids connector as rdf string.
     * @throws ConstraintViolationException If the response could not be extracted.
     */
    public static String getConnectorAsRdf(final BaseConnector baseConnector) throws ConstraintViolationException {
        return baseConnector.toRdf();
    }

    /**
     * Get rdf string from instance of type {@link Resource}.
     *
     * @param resource The ids resource.
     * @return The ids resource as rdf string.
     * @throws ConstraintViolationException If the response could not be extracted.
     */
    public static String getResourceAsRdf(final Resource resource) throws ConstraintViolationException {
        return resource.toRdf();
    }

    public static String getArtifactAsRdf(final Artifact artifact) throws ConstraintViolationException {
        return artifact.toRdf();
    }

    public static String getRepresentationAsRdf(final Representation representation) throws ConstraintViolationException {
        return representation.toRdf();
    }

    public static String getContractRequestAsRdf(final ContractRequest request) throws ConstraintViolationException {
        return request.toRdf();
    }

    /**
     * Converts a date to XMLGregorianCalendar format.
     *
     * @param date the date object.
     * @return the XMLGregorianCalendar object or null.
     */
    public XMLGregorianCalendar getGregorianOf(final Date date) {
        GregorianCalendar c = new GregorianCalendar();
        c.setTime(date);
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
        } catch (DatatypeConfigurationException exception) {
            // Rethrow but do not register in function header
            throw new RuntimeException(exception);
        }
    }
}
