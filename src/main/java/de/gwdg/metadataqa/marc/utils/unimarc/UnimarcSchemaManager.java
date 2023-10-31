package de.gwdg.metadataqa.marc.utils.unimarc;


import java.util.HashMap;
import java.util.Map;

public class UnimarcSchemaManager {
    Map<String, UnimarcFieldDefinition> fieldDirectory = new HashMap<>();

    public void add(UnimarcFieldDefinition fieldDefinition) {
        fieldDirectory.put(fieldDefinition.getTag(), fieldDefinition);
    }

    public int size() {
        return fieldDirectory.size();
    }

    public UnimarcFieldDefinition lookup(String searchTerm) {
        return fieldDirectory.get(searchTerm);
    }
}
