package de.gwdg.metadataqa.marc;

import de.gwdg.metadataqa.api.json.DataElement;
import de.gwdg.metadataqa.api.model.XmlFieldInstance;
import de.gwdg.metadataqa.api.model.selector.JsonSelector;
import de.gwdg.metadataqa.api.schema.MarcJsonSchema;
import de.gwdg.metadataqa.api.schema.Schema;
import de.gwdg.metadataqa.marc.cli.utils.IteratorResponse;
import de.gwdg.metadataqa.marc.dao.Control001;
import de.gwdg.metadataqa.marc.dao.Control003;
import de.gwdg.metadataqa.marc.dao.Control005;
import de.gwdg.metadataqa.marc.dao.Control006;
import de.gwdg.metadataqa.marc.dao.Control007;
import de.gwdg.metadataqa.marc.dao.Control008;
import de.gwdg.metadataqa.marc.dao.DataField;
import de.gwdg.metadataqa.marc.dao.Marc21Leader;
import de.gwdg.metadataqa.marc.dao.MarcLeader;
import de.gwdg.metadataqa.marc.dao.UnimarcLeader;
import de.gwdg.metadataqa.marc.dao.record.BibliographicRecord;
import de.gwdg.metadataqa.marc.dao.record.Marc21Record;
import de.gwdg.metadataqa.marc.dao.record.PicaRecord;
import de.gwdg.metadataqa.marc.dao.record.UnimarcRecord;
import de.gwdg.metadataqa.marc.definition.MarcVersion;
import de.gwdg.metadataqa.marc.definition.TagDefinitionLoader;
import de.gwdg.metadataqa.marc.definition.structure.DataFieldDefinition;
import de.gwdg.metadataqa.marc.definition.structure.SubfieldDefinition;
import de.gwdg.metadataqa.marc.utils.MapToDatafield;
import de.gwdg.metadataqa.marc.utils.alephseq.AlephseqLine;
import de.gwdg.metadataqa.marc.utils.alephseq.MarcMakerLine;
import de.gwdg.metadataqa.marc.utils.alephseq.MarclineLine;
import de.gwdg.metadataqa.marc.utils.pica.PicaDataField;
import de.gwdg.metadataqa.marc.utils.pica.PicaSchemaManager;
import de.gwdg.metadataqa.marc.utils.pica.PicaSubfield;
import de.gwdg.metadataqa.marc.utils.pica.reader.model.PicaLine;
import de.gwdg.metadataqa.marc.utils.unimarc.UnimarcDataFieldImpl;
import de.gwdg.metadataqa.marc.utils.unimarc.UnimarcSchemaManager;
import net.minidev.json.JSONArray;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.impl.ControlFieldImpl;
import org.marc4j.marc.impl.DataFieldImpl;
import org.marc4j.marc.impl.LeaderImpl;
import org.marc4j.marc.impl.RecordImpl;
import org.marc4j.marc.impl.SubfieldImpl;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory class to create MarcRecord from JsonSelector
 */
public class MarcFactory {

  private static final Logger logger = Logger.getLogger(MarcFactory.class.getCanonicalName());
  private static final List<String> fixableControlFields = Arrays.asList("006", "007", "008");

  private static Schema schema = new MarcJsonSchema();

  private MarcFactory() {
    throw new IllegalStateException("This is a utility class, can not be instantiated");
  }

  public static BibliographicRecord create(JsonSelector selector) {
    return create(selector, MarcVersion.MARC21);
  }

  public static BibliographicRecord create(JsonSelector selector, MarcVersion version) {
    var marcRecord = new Marc21Record();
    for (DataElement dataElement : schema.getPaths()) {
      if (dataElement.getParent() != null)
        continue;
      switch (dataElement.getLabel()) {
        case "leader":
          marcRecord.setLeader(new Marc21Leader(extractFirst(selector, dataElement)));
          break;
        case "001":
          marcRecord.setControl001(new Control001(extractFirst(selector, dataElement)));
          break;
        case "003":
          marcRecord.setControl003(new Control003(extractFirst(selector, dataElement)));
          break;
        case "005":
          marcRecord.setControl005(new Control005(extractFirst(selector, dataElement), marcRecord));
          break;
        case "006":
          marcRecord.setControl006(
            new Control006(extractFirst(selector, dataElement), marcRecord));
          break;
        case "007":
          marcRecord.setControl007(
            new Control007(extractFirst(selector, dataElement), marcRecord));
          break;
        case "008":
          marcRecord.setControl008(
            new Control008(extractFirst(selector, dataElement), marcRecord));
          break;
        default:
          JSONArray fieldInstances = (JSONArray) selector.getFragment(dataElement.getPath());
          for (var fieldInsanceNr = 0; fieldInsanceNr < fieldInstances.size(); fieldInsanceNr++) {
            var fieldInstance = (Map) fieldInstances.get(fieldInsanceNr);
            var field = MapToDatafield.parse(fieldInstance, version);
            if (field != null) {
              marcRecord.addDataField(field);
              field.setMarcRecord(marcRecord);
            } else {
              marcRecord.addUnhandledTags(dataElement.getLabel());
            }
          }
          break;
      }
    }
    return marcRecord;
  }

  public static BibliographicRecord createFromMarc4j(Record marc4jRecord) {
    return createFromMarc4j(marc4jRecord, null, MarcVersion.MARC21);
  }

  public static BibliographicRecord createFromMarc4j(Record marc4jRecord,
                                                     MarcLeader.Type defaultType) {
    return createFromMarc4j(marc4jRecord, defaultType, MarcVersion.MARC21);
  }

  public static BibliographicRecord createFromMarc4j(Record marc4jRecord,
                                                     MarcVersion marcVersion) {
    return createFromMarc4j(marc4jRecord, null, marcVersion);
  }

  public static BibliographicRecord createFromMarc4j(Record marc4jRecord,
                                                     MarcLeader.Type defaultType,
                                                     MarcVersion marcVersion) {
    return createMarc21FromMarc4j(marc4jRecord, defaultType, marcVersion, null);
  }

  /**
   * Create a MarcRecord object from Marc4j object by setting the leader, control fields and data fields.
   * @param marc4jRecord The Marc4j record
   * @param defaultType The defauld document type
   * @param marcVersion The MARC version
   * @param replacementInControlFields A ^ or # character which sould be replaced with space in control fields
   */
  public static BibliographicRecord createMarc21FromMarc4j(Record marc4jRecord,
                                                           MarcLeader.Type defaultType,
                                                           MarcVersion marcVersion,
                                                           String replacementInControlFields) {
    var marcRecord = new Marc21Record();

    if (marc4jRecord.getLeader() != null) {
      String data = marc4jRecord.getLeader().marshal();
      if (replacementInControlFields != null)
        data = data.replace(replacementInControlFields, " ");
      marcRecord.setLeader(new Marc21Leader(data, defaultType));

      if (marcRecord.getType() == null) {
        throw new InvalidParameterException(
          String.format(
            "Error in '%s': no type has been detected. Leader: '%s'.",
            marc4jRecord.getControlNumberField(), marcRecord.getLeader().getLeaderString()
          )
        );
      }
    }

    importMarc4jControlFields(marc4jRecord, marcRecord, replacementInControlFields);

    importMarc4jDataFields(marc4jRecord, marcRecord, marcVersion);

    return marcRecord;
  }

  public static BibliographicRecord createPicaFromMarc4j(Record marc4jRecord, PicaSchemaManager picaSchemaManager) {
    var marcRecord = new PicaRecord();
    importMarc4jControlFields(marc4jRecord, marcRecord, null);
    importMarc4jDataFields(marc4jRecord, marcRecord, picaSchemaManager);

    return marcRecord;
  }

  public static BibliographicRecord createUnimarcFromMarc4j(Record marc4jRecord,
                                                            MarcLeader.Type defaultType,
                                                            UnimarcSchemaManager unimarcSchemaManager) {
    var marcRecord = new UnimarcRecord();

    if (marc4jRecord.getLeader() != null) {
      String data = marc4jRecord.getLeader().marshal();
      UnimarcLeader leader = new UnimarcLeader(unimarcSchemaManager.getLeaderDefinition(), data, defaultType);
      leader.initialize();
      marcRecord.setLeader(leader);
    }

    importMarc4jControlFields(marc4jRecord, marcRecord, null);
    importMarc4jDataFields(marc4jRecord, marcRecord, unimarcSchemaManager);

    return marcRecord;
  }

  /**
   * As a part of transormation of the marc4jRecord into marcRecord, imports control fields from the former to the latter
   * @param marc4jRecord The Marc4j record being transformed into BibliographicRecord
   * @param marcRecord Can be Marc21Record, PicaRecord or UnimarcRecord
   * @param replacementInControlFields Usually a ^ or # character which should be replaced with space in control fields' data
   */
  private static void importMarc4jControlFields(Record marc4jRecord,
                                                BibliographicRecord marcRecord,
                                                String replacementInControlFields) {
    if (marc4jRecord.getControlFields() == null)
      return;

    for (ControlField controlField : marc4jRecord.getControlFields()) {
      String data = controlField.getData();
      if (replacementInControlFields != null && isFixable(controlField.getTag()))
        data = data.replace(replacementInControlFields, " ");
      switch (controlField.getTag()) {
        case "001":
          marcRecord.setControl001(new Control001(data)); break;
        case "003":
          marcRecord.setControl003(new Control003(data)); break;
        case "005":
          marcRecord.setControl005(new Control005(data, marcRecord)); break;
        case "006":
          marcRecord.setControl006(new Control006(data, marcRecord)); break;
        case "007":
          marcRecord.setControl007(new Control007(data, marcRecord)); break;
        case "008":
          marcRecord.setControl008(new Control008(data, marcRecord)); break;
        default:
          break;
      }
    }
  }

  private static boolean isFixable(String tag) {
    return fixableControlFields.contains(tag);
  }

  private static void importMarc4jDataFields(Record marc4jRecord,
                                             BibliographicRecord marcRecord,
                                             MarcVersion marcVersion) {
    for (org.marc4j.marc.DataField dataField : marc4jRecord.getDataFields()) {
      var definition = getDataFieldDefinition(dataField, marcVersion);
      if (definition == null) {
        marcRecord.addUnhandledTags(dataField.getTag());
      }
      var field = extractDataField(dataField, definition, marcVersion);
      marcRecord.addDataField(field);
    }
  }

  private static void importMarc4jDataFields(Record marc4jRecord,
                                             BibliographicRecord marcRecord,
                                             PicaSchemaManager schema) {
    for (org.marc4j.marc.DataField dataField : marc4jRecord.getDataFields()) {
      // todo discuss this as it feels as if it's never going to be an instance of PicaDataField
      boolean isPica = dataField instanceof PicaDataField;
      PicaDataField picadf = isPica ? (PicaDataField) dataField : null;
      var definition = isPica
        ? schema.lookup(picadf)
        : schema.lookup(dataField.getTag());

      if (definition == null)
        marcRecord.addUnhandledTags(isPica && picadf != null ? picadf.getFullTag() : dataField.getTag());

      var field = extractDataField(dataField, definition, MarcVersion.MARC21);
      marcRecord.addDataField(field);
    }
  }

  // This method could probably be merged with the respective Pica method after the discussing
  private static void importMarc4jDataFields(Record marc4jRecord,
                                             BibliographicRecord marcRecord,
                                             UnimarcSchemaManager schema) {
    for (org.marc4j.marc.DataField dataField : marc4jRecord.getDataFields()) {
      // todo discuss this as it feels as if it's never going to be an instance of UnimarcDataField
      boolean isUnimarc = dataField instanceof UnimarcDataFieldImpl;
      UnimarcDataFieldImpl unimarcDf = isUnimarc ? (UnimarcDataFieldImpl) dataField : null;
      var definition = isUnimarc
          ? schema.lookup(unimarcDf)
          : schema.lookup(dataField.getTag());

      if (definition == null) {
        marcRecord.addUnhandledTags(unimarcDf != null ? unimarcDf.getTag() : dataField.getTag());
      }

      var field = extractDataField(dataField, definition, MarcVersion.MARC21);
      marcRecord.addDataField(field);
    }
  }

  public static DataFieldDefinition getDataFieldDefinition(org.marc4j.marc.DataField dataField,
                                                           MarcVersion marcVersion) {
    return getDataFieldDefinition(dataField.getTag(), marcVersion);
  }

  public static DataFieldDefinition getDataFieldDefinition(String tag, MarcVersion marcVersion) {
    return TagDefinitionLoader.load(tag, marcVersion);
  }

  private static DataField extractDataField(org.marc4j.marc.DataField dataField,
                                            DataFieldDefinition definition,
                                            MarcVersion marcVersion) {
    DataField field;
    if (definition == null) {
      field = new DataField(dataField.getTag(),
              Character.toString(dataField.getIndicator1()),
              Character.toString(dataField.getIndicator2()),
              marcVersion
      );
    } else {
      field = new DataField(
              definition,
              Character.toString(dataField.getIndicator1()),
              Character.toString(dataField.getIndicator2())
      );
    }
    for (Subfield subfield : dataField.getSubfields()) {
      var code = Character.toString(subfield.getCode());
      SubfieldDefinition subfieldDefinition = definition == null ? null : definition.getSubfield(code);
      MarcSubfield marcSubfield;
      if (subfieldDefinition == null) {
        marcSubfield = new MarcSubfield(null, code, subfield.getData());
      } else {
        marcSubfield = new MarcSubfield(subfieldDefinition, code, subfield.getData());
      }
      marcSubfield.setField(field);
      field.getSubfields().add(marcSubfield);
    }
    field.indexSubfields();

    if (dataField instanceof PicaDataField) {
      PicaDataField df = (PicaDataField)dataField;
      if (df.getOccurrence() != null)
        field.setOccurrence(df.getOccurrence());
    }

    return field;
  }

  private static List<String> extractList(JsonSelector selector, DataElement dataElement) {
    List<XmlFieldInstance> instances = selector.get(dataElement.getPath());
    List<String> values = new ArrayList<>();
    if (instances != null)
      for (XmlFieldInstance instance : instances)
        values.add(instance.getValue());
    return values;
  }

  private static String extractFirst(JsonSelector selector, DataElement dataElement) {
    List<String> list = extractList(selector, dataElement);
    if (!list.isEmpty())
      return list.get(0);
    return null;
  }

  public static BibliographicRecord createFromFormattedText(String marcRecordAsText) {
    return createFromFormattedText(Arrays.asList(marcRecordAsText.split("\n")));
  }

  public static BibliographicRecord createFromFormattedText(String marcRecordAsText, MarcVersion marcVersion) {
    return createFromFormattedText(Arrays.asList(marcRecordAsText.split("\n")), marcVersion);
  }

  public static BibliographicRecord createFromFormattedText(List<String> lines) {
    return createFromFormattedText(lines, MarcVersion.MARC21);
  }

  public static BibliographicRecord createFromFormattedText(List<String> lines, MarcVersion marcVersion) {
    if (marcVersion == null)
      marcVersion = MarcVersion.MARC21;

    var marcRecord = new Marc21Record();
    for (String line : lines) {
      if (line.startsWith("LEADER ")) {
        marcRecord.setLeader(line.replace("LEADER ", ""), marcVersion);
      } else {
        var tag = line.substring(0, 3);
        var content = line.substring(4);
        marcRecord.setField(tag, content, marcVersion);
      }
    }
    return marcRecord;
  }

  public static BibliographicRecord createFromAlephseq(List<AlephseqLine> lines,
                                                       MarcVersion marcVersion) {
    if (marcVersion == null)
      marcVersion = MarcVersion.MARC21;

    var marcRecord = new Marc21Record();
    for (AlephseqLine line : lines) {
      if (line.isLeader()) {
        marcRecord.setMarc21Leader(line.getContent());
      } else if (line.isNumericTag()) {
        marcRecord.setField(line.getTag(), line.getInd1(), line.getInd2(), line.getContent(), marcVersion);
      }
    }
    return marcRecord;
  }

  public static IteratorResponse createRecordFromAlephseq(List<AlephseqLine> lines) {
    IteratorResponse response = new IteratorResponse();
    Record marc4jRecord = new RecordImpl();
    for (AlephseqLine line : lines) {
      if (response.getRecordId() == null)
        response.setRecordId(line.getRecordID());
      if (line.isLeader()) {
        String leader = line.getContent();
        if (leader.length() == 24)
          marc4jRecord.setLeader(new LeaderImpl(line.getContent()));
        else {
          response.addError(line.getRecordID(), "Leader length is not 24 char long, but " + leader.length());
          logger.log(Level.WARNING, "Leader line length is not 24 char long, but {3}. Record id: {0}, tag {1}, value: \"{2}\"",
                  new Object[]{line.getRecordID(), line.getTag(), line.getRawContent(), leader.length()});
        }
      } else if (line.isNumericTag()) {
        if (line.isControlField()) {
          marc4jRecord.addVariableField(new ControlFieldImpl(line.getTag(), line.getContent()));
        } else {
          var df = new DataFieldImpl(
            line.getTag(), line.getInd1().charAt(0), line.getInd2().charAt(0)
          );
          for (String[] pair : line.parseSubfields()) {
            if (pair.length == 2 && pair[0] != null && pair[1] != null) {
              df.addSubfield(new SubfieldImpl(pair[0].charAt(0), pair[1]));
            } else {
              response.addError(line.getRecordID(), line.getTag(), line.getRawContent());
              logger.log(Level.WARNING, "parse error in record #{0}) tag {1}: \"{2}\"",
                new Object[]{line.getRecordID(), line.getTag(), line.getRawContent()});
            }
          }
          marc4jRecord.addVariableField(df);
        }
      }
    }
    response.setMarc4jRecord(marc4jRecord);
    return response;
  }

  public static Record createRecordFromMarcline(List<MarclineLine> lines) {
    Record marc4jRecord = new RecordImpl();
    for (MarclineLine line : lines) {
      processMarclineLine(marc4jRecord, line);
    }
    return marc4jRecord;
  }

  public static Record createRecordFromMarcMaker(List<MarcMakerLine> lines) {
    Record marc4jRecord = new RecordImpl();
    for (MarcMakerLine line : lines) {
      if (line.isLeader()) {
        try {
          marc4jRecord.setLeader(new LeaderImpl(line.getContent()));
        } catch (StringIndexOutOfBoundsException e) {
          logger.severe("Error at creating leader: " + e.getMessage());
        }
      } else if (line.isNumericTag()) {
        if (line.isControlField()) {
          marc4jRecord.addVariableField(new ControlFieldImpl(line.getTag(), line.getContent()));
        } else {
          var df = new DataFieldImpl(line.getTag(), line.getInd1().charAt(0), line.getInd2().charAt(0));
          for (String[] pair : line.parseSubfields()) {
            if (pair.length == 2 && pair[0] != null && pair[1] != null) {
              df.addSubfield(new SubfieldImpl(pair[0].charAt(0), pair[1]));
            } else {
              logger.log(Level.WARNING, "parse error in record #{0}) tag {1]: \"{2]\"",
                new Object[]{line.getRecordID(), line.getTag(), line.getRawContent()});
            }
          }
          marc4jRecord.addVariableField(df);
        }
      }
    }
    return marc4jRecord;
  }

  public static Record createRecordFromPica(List<PicaLine> lines,
                                            String idField,
                                            String idCode,
                                            PicaSchemaManager schema) {
    Record marc4jRecord = new RecordImpl();
    String id = null;
    for (PicaLine line : lines) {
      org.marc4j.marc.DataField df = new PicaDataField(line.getTag(), line.getOccurrence());
      for (PicaSubfield picaSubfield : line.getSubfields()) {
        df.addSubfield(new SubfieldImpl(picaSubfield.getCode().charAt(0), picaSubfield.getValue()));
        if (line.getTag().equals(idField) && picaSubfield.getCode().equals(idCode))
          id = picaSubfield.getValue();
      }
      marc4jRecord.addVariableField(df);
    }
    if (id != null)
      marc4jRecord.addVariableField(new ControlFieldImpl("001", id));
    return marc4jRecord;
  }

  /**
   * Process a single line of a MARC record and makes the appropriate changes in the marc4jRecord
   * @param marc4jRecord The marc4j record being created
   * @param line The MarcLine line being processed
   */
  private static void processMarclineLine(Record marc4jRecord, MarclineLine line) {
    if (line.isLeader()) {
      try {
        marc4jRecord.setLeader(new LeaderImpl(line.getContent()));
      } catch (StringIndexOutOfBoundsException e) {
        logger.severe("Error at creating leader: " + e.getMessage());
      }
      return;
    }

    // If the line is not a leader, then it's either a control field or a data field, so it has to have a numeric tag
    if (!line.isNumericTag()) {
      return;
    }

    if (line.isControlField()) {
      ControlFieldImpl controlField = new ControlFieldImpl(line.getTag(), line.getContent());
      marc4jRecord.addVariableField(controlField);
      return;
    }

    var df = new DataFieldImpl(line.getTag(), line.getInd1().charAt(0), line.getInd2().charAt(0));

    for (String[] pair : line.parseSubfields()) {
      if (pair.length == 2 && pair[0] != null && pair[1] != null) {
        df.addSubfield(new SubfieldImpl(pair[0].charAt(0), pair[1]));
      } else {
        logger.log(Level.WARNING, "parse error in record #{0}) tag {1}: \"{2}\"",
            new Object[]{line.getRecordID(), line.getTag(), line.getRawContent()});
      }
    }
    marc4jRecord.addVariableField(df);
  }
}
