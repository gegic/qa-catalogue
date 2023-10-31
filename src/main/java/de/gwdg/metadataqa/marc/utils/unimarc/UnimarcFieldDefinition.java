package de.gwdg.metadataqa.marc.utils.unimarc;

import de.gwdg.metadataqa.marc.definition.Cardinality;
import de.gwdg.metadataqa.marc.definition.bibliographic.BibliographicFieldDefinition;
import de.gwdg.metadataqa.marc.definition.structure.SubfieldDefinition;

import java.util.HashMap;
import java.util.Map;


/**
 * Represents a field definition in the Unimarc schema.
 */
public class UnimarcFieldDefinition implements BibliographicFieldDefinition {
    private final String tag;
    private final String label;
    private final Cardinality cardinality;
    private final boolean required;
    private final Map<String, SubfieldDefinition> subfieldDefinitions = new HashMap<>();

    public UnimarcFieldDefinition(String tag, String label, boolean repeatable, boolean required) {
        this.tag = tag;
        this.label = label;
        this.cardinality = repeatable ? Cardinality.Repeatable : Cardinality.Nonrepeatable;
        this.required = required;
    }

    public void addSubfieldDefinition(SubfieldDefinition subfieldDefinition) {
        subfieldDefinitions.put(subfieldDefinition.getCode(), subfieldDefinition);
        subfieldDefinition.setParent(this);
    }

    public Map<String, SubfieldDefinition> getSubfieldDefinitions() {
        return subfieldDefinitions;
    }

    public boolean isRepeatable() {
        return cardinality == Cardinality.Repeatable;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public String getTag() {
        return tag;
    }

    @Override
    public Cardinality getCardinality() {
        return cardinality;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getDescriptionUrl() {
        return null;
    }
}
