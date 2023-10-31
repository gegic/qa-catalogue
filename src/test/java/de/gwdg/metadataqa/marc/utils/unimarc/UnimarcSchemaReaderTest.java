package de.gwdg.metadataqa.marc.utils.unimarc;

import de.gwdg.metadataqa.marc.definition.general.codelist.CodeList;
import de.gwdg.metadataqa.marc.definition.structure.SubfieldDefinition;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;

public class UnimarcSchemaReaderTest {
    @Test
    public void createSchema_createsAllFields() {
        UnimarcSchemaReader unimarcReader = new UnimarcSchemaReader();
        String path = getPath("unimarc/avram-unimarc.json");
        UnimarcSchemaManager schema = unimarcReader.createSchema(path);

        assertEquals(192, schema.size());
    }

    /**
     * As it would be counterproductive to test the structure of every single field,
     * this test only checks if the structure of the first and last field is correct.
     */
    @Test
    public void createSchema_fieldStructureIsCorrect() {
        UnimarcSchemaReader unimarcReader = new UnimarcSchemaReader();
        String path = getPath("unimarc/avram-unimarc.json");
        UnimarcSchemaManager schema = unimarcReader.createSchema(path);

        // Assert that there are fields with tag 001 and 886 in the schema, as well as
        // their respective data.
        UnimarcFieldDefinition field001 = schema.lookup("001");
        UnimarcFieldDefinition expectedField001 = new UnimarcFieldDefinition(
                "001",
                "RECORD IDENTIFIER",
                false,
                true
        );
        assertEquals(expectedField001.getTag(), field001.getTag());
        assertEquals(expectedField001.getLabel(), field001.getLabel());
        assertEquals(expectedField001.getCardinality(), field001.getCardinality());
        assertEquals(expectedField001.isRequired(), field001.isRequired());

        UnimarcFieldDefinition field886 = schema.lookup("886");
        UnimarcFieldDefinition expectedField886 = new UnimarcFieldDefinition(
                "886",
                "DATA NOT CONVERTED FROM SOURCE FORMAT",
                true,
                false
        );
        assertEquals(expectedField886.getTag(), field886.getTag());
        assertEquals(expectedField886.getLabel(), field886.getLabel());
        assertEquals(expectedField886.getCardinality(), field886.getCardinality());
        assertEquals(expectedField886.isRequired(), field886.isRequired());
    }

    /**
     * As it would be counterproductive to test the structure of every single subfield,
     * this test only checks if the structure of the subfields of the last field is correct
     * (as it contains a codelist), and the field 100 as it contains positions.
     * The reader is after all expected to read subfields and not to make sure that the
     * structure of the subfields is correct.
     */
    @Test
    public void createSchema_subfieldStructureIsCorrect() {
        UnimarcSchemaReader unimarcReader = new UnimarcSchemaReader();
        String path = getPath("unimarc/avram-unimarc.json");
        UnimarcSchemaManager schema = unimarcReader.createSchema(path);

        int expectedSubfieldCount = 3;
        int expectedSubfield2CodeListSize = 56;
        UnimarcFieldDefinition lastField = schema.lookup("886");
        Map<String, SubfieldDefinition> subfieldDefinitions = lastField.getSubfieldDefinitions();

        assertEquals(expectedSubfieldCount, subfieldDefinitions.size());

        SubfieldDefinition subfieldA = subfieldDefinitions.get("a");
        assertNotNull(subfieldA);
        assertTrue(subfieldA.isRepeatable());

        SubfieldDefinition subfieldB = subfieldDefinitions.get("b");
        assertNotNull(subfieldB);
        assertTrue(subfieldA.isRepeatable());

        SubfieldDefinition subfield2 = subfieldDefinitions.get("2");
        assertNotNull(subfield2);
        assertFalse(subfield2.isRepeatable());

        CodeList codeList = subfield2.getCodeList();
        assertNotNull(codeList);
        assertEquals(expectedSubfield2CodeListSize, codeList.getCodes().size());


        UnimarcFieldDefinition field100 = schema.lookup("100");
        Map<String, SubfieldDefinition> subfieldDefinitions100 = field100.getSubfieldDefinitions();
        SubfieldDefinition subfield100a = subfieldDefinitions100.get("a");
        assertNotNull(subfield100a);

        // TODO Not done yet
//        assertEquals(x, subfield100a.getPositions().size());
    }
    private String getPath(String filename) {
        return Paths.get("src/test/resources/" + filename).toAbsolutePath().toString();
    }
}
