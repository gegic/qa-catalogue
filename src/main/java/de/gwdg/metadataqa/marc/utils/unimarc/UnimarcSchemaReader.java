package de.gwdg.metadataqa.marc.utils.unimarc;

import de.gwdg.metadataqa.marc.EncodedValue;
import de.gwdg.metadataqa.marc.definition.structure.SubfieldDefinition;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads a UNIMARC schema from a JSON file, so that it can be used to process UNIMARC records.
 */
public class UnimarcSchemaReader {
    private static final Logger logger = Logger.getLogger(UnimarcSchemaReader.class.getCanonicalName());
    private final JSONParser parser = new JSONParser(JSONParser.MODE_RFC4627);
    private final UnimarcSchemaManager schema = new UnimarcSchemaManager();

    public UnimarcSchemaManager createSchema(InputStream inputStream) {
        try {
            JSONObject obj = readFile(inputStream);
            processFields(obj);
        } catch (ParseException e) {
            logger.severe(e.getLocalizedMessage());
        }

        return schema;
    }

    public UnimarcSchemaManager createSchema(String filename) {
        try {
            JSONObject obj = readFile(filename);
            processFields(obj);
        } catch (FileNotFoundException | ParseException e) {
            logger.severe(e.getLocalizedMessage());
        }

        return schema;
    }

    private JSONObject readFile(String filename) throws FileNotFoundException, ParseException {
        FileReader reader = new FileReader(filename);
        return (JSONObject) parser.parse(reader);
    }

    private JSONObject readFile(InputStream stream) throws ParseException {
        InputStreamReader streamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        return (JSONObject) parser.parse(streamReader);
    }

    private void processFields(JSONObject obj) {

        JSONObject fields = (JSONObject) obj.get("fields");
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String tag = entry.getKey();
            JSONObject jsonField = (JSONObject) fields.get(tag);

            // In this situation, it isn't necessary to access the JSON value of 'tag' directly,
            // as it is already available as the key of the UNIMARC field.
            UnimarcFieldDefinition fieldDefinition = new UnimarcFieldDefinition(
                    tag,
                    (String) jsonField.get("label"),
                    (boolean) jsonField.get("repeatable"),
                    (boolean) jsonField.get("required")
            );

            addSubfields(jsonField, fieldDefinition);

            schema.add(fieldDefinition);
        }
    }

    private void addSubfields(JSONObject jsonField, UnimarcFieldDefinition fieldDefinition) {
        // Subfields are a JSON object in our schema
        JSONObject subfields = (JSONObject) jsonField.get("subfields");
        if (subfields == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : subfields.entrySet()) {
            String code = entry.getKey();
            // Avoid personally defined JSON comments
            if (code.startsWith("//")) {
                continue;
            }

            JSONObject jsonSubfield = (JSONObject) subfields.get(code);
            // In this situation, it isn't necessary to access the JSON value of 'code' directly,
            // as it is already available as the key of the UNIMARC subfield.
            Object repeatable = jsonSubfield.get("repeatable");

            SubfieldDefinition subfieldDefinition = new SubfieldDefinition(
                    code,
                    (String) jsonSubfield.get("label"),
                    repeatable != null && (boolean) repeatable
            );

            addCodelist(jsonSubfield, subfieldDefinition);
            // TODO process positions

            fieldDefinition.addSubfieldDefinition(subfieldDefinition);
        }
    }

    private void addCodelist(JSONObject jsonSubfield, SubfieldDefinition subfieldDefinition) {
        JSONObject codelist = (JSONObject) jsonSubfield.get("codelist");
        if (codelist == null) {
            return;
        }
        UnimarcCodeList codeList = new UnimarcCodeList();
        for (Map.Entry<String, Object> entry : codelist.entrySet()) {
            String code = entry.getKey();
            String codeLabel = (String) codelist.get(code);

            EncodedValue encodedValue = new EncodedValue(code, codeLabel);
            codeList.addCode(encodedValue);
        }

        subfieldDefinition.setCodeList(codeList);
    }
}
