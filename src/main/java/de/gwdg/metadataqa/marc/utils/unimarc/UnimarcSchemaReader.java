package de.gwdg.metadataqa.marc.utils.unimarc;

import de.gwdg.metadataqa.marc.EncodedValue;
import de.gwdg.metadataqa.marc.definition.structure.ControlfieldPositionDefinition;
import de.gwdg.metadataqa.marc.definition.structure.Indicator;
import de.gwdg.metadataqa.marc.definition.structure.SubfieldDefinition;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads a UNIMARC schema from a JSON file, so that it can be used to process UNIMARC records.
 */
public class UnimarcSchemaReader {
    private static final String LABEL = "label";
    private static final String REPEATABLE = "repeatable";
    private static final String REQUIRED = "required";
    private static final String START = "start";
    private static final String END = "end";

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
                    (String) jsonField.get(LABEL),
                    (boolean) jsonField.get(REPEATABLE),
                    (boolean) jsonField.get(REQUIRED)
            );

            Indicator indicator1 = getIndicator(1, jsonField);
            fieldDefinition.setInd1(indicator1);
            Indicator indicator2 = getIndicator(2, jsonField);
            fieldDefinition.setInd2(indicator2);
            List<SubfieldDefinition> subfieldDefinitions = getSubfields(jsonField);
            fieldDefinition.setSubfieldDefinitions(subfieldDefinitions);


            schema.add(fieldDefinition);
        }
    }

    /**
     * @param indicatorNumber Either 1 or 2
     * @param jsonField The JSON object of the field
     */
    private Indicator getIndicator(int indicatorNumber, JSONObject jsonField) {
        JSONObject jsonIndicator = (JSONObject) jsonField.get("indicator" + indicatorNumber);
        if (jsonIndicator == null) {
            // Return an empty indicator which represent the empty values in order to conform with MARC21 fields
            return new Indicator();
        }

        Indicator indicator = new Indicator((String) jsonIndicator.get(LABEL));
        List<EncodedValue> codes = getCodes(jsonIndicator, "codes");
        indicator.setCodes(codes);

        return indicator;
    }

    private List<SubfieldDefinition> getSubfields(JSONObject jsonField) {
        // Subfields are a JSON object in our schema
        JSONObject subfields = (JSONObject) jsonField.get("subfields");
        if (subfields == null) {
            return List.of();
        }

        List<SubfieldDefinition> subfieldDefinitions = new ArrayList<>();

        for (Map.Entry<String, Object> entry : subfields.entrySet()) {
            String code = entry.getKey();
            // Avoid personally defined JSON comments
            if (code.startsWith("//")) {
                continue;
            }

            JSONObject jsonSubfield = (JSONObject) subfields.get(code);
            // In this situation, it isn't necessary to access the JSON value of 'code' directly,
            // as it is already available as the key of the UNIMARC subfield.
            Object repeatable = jsonSubfield.get(REPEATABLE);

            SubfieldDefinition subfieldDefinition = new SubfieldDefinition(
                    code,
                    (String) jsonSubfield.get(LABEL),
                    repeatable != null && (boolean) repeatable
            );

            List<EncodedValue> codes = getCodes(jsonSubfield, "codelist");
            UnimarcCodeList codeList = new UnimarcCodeList();
            codeList.setCodes(codes);
            subfieldDefinition.setCodeList(codeList);

            List<ControlfieldPositionDefinition> positions = getPositions(jsonSubfield);
            subfieldDefinition.setPositions(positions);

            subfieldDefinitions.add(subfieldDefinition);
        }
        return subfieldDefinitions;
    }

    private List<ControlfieldPositionDefinition> getPositions(JSONObject jsonSubfield) {
        JSONObject positions = (JSONObject) jsonSubfield.get("positions");
        if (positions == null) {
            return List.of();
        }
        List<ControlfieldPositionDefinition> positionDefinitions = new ArrayList<>();
        for (Map.Entry<String, Object> positionEntry : positions.entrySet()) {

            if (positionEntry.getKey().startsWith("//")) {
                continue;
            }

            JSONObject position = (JSONObject) positionEntry.getValue();

            int positionStart = (int) position.get(START);
            Object positionEndObject = position.get(END);

            // As the implementation of ControlfieldPositionDefinition requires a positionEnd, and it seems
            // to be slightly different to what is specified in the UNIMARC manuals, we add 1 to the positionEnd
            int positionEnd = (positionEndObject == null ? positionStart : (int) positionEndObject) + 1;

            ControlfieldPositionDefinition positionDefinition = new ControlfieldPositionDefinition(
                    (String) position.get(LABEL),
                    positionStart,
                    positionEnd
            );
            List<EncodedValue> codes = getCodes(position, "codes");
            positionDefinition.setCodes(codes);

            positionDefinitions.add(positionDefinition);
        }
        return positionDefinitions;
    }

    /**
     * Retrieves the codes from the JSON object
     * @param codesHolder Meant to be either an indicator or a position
     * @return A list of codes
     */
    private List<EncodedValue> getCodes(JSONObject codesHolder, String objectKey) {
        JSONObject codes = (JSONObject) codesHolder.get(objectKey);
        if (codes == null) {
            return List.of();
        }
        List<EncodedValue> encodedValues = new ArrayList<>();
        for (Map.Entry<String, Object> codeEntry : codes.entrySet()) {
            String code = codeEntry.getKey();
            String codeLabel = (String) codes.get(code);

            EncodedValue encodedValue = new EncodedValue(code, codeLabel);
            encodedValues.add(encodedValue);
        }

        return encodedValues;
    }
}
