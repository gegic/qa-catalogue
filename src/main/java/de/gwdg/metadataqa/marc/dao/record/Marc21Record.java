package de.gwdg.metadataqa.marc.dao.record;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gwdg.metadataqa.marc.MarcFactory;
import de.gwdg.metadataqa.marc.analysis.shelfready.ShelfReadyFieldsBooks;
import de.gwdg.metadataqa.marc.dao.Control001;
import de.gwdg.metadataqa.marc.dao.Control003;
import de.gwdg.metadataqa.marc.dao.Control005;
import de.gwdg.metadataqa.marc.dao.Control006;
import de.gwdg.metadataqa.marc.dao.Control007;
import de.gwdg.metadataqa.marc.dao.Control008;
import de.gwdg.metadataqa.marc.dao.DataField;
import de.gwdg.metadataqa.marc.dao.MarcControlField;
import de.gwdg.metadataqa.marc.dao.MarcPositionalControlField;
import de.gwdg.metadataqa.marc.definition.MarcVersion;
import de.gwdg.metadataqa.marc.definition.bibliographic.SchemaType;
import de.gwdg.metadataqa.marc.definition.structure.ControlfieldPositionDefinition;
import de.gwdg.metadataqa.marc.definition.structure.DataFieldDefinition;
import de.gwdg.metadataqa.marc.model.SolrFieldType;
import de.gwdg.metadataqa.marc.utils.marcspec.legacy.MarcSpec;
import de.gwdg.metadataqa.marc.utils.unimarc.UnimarcConverter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Marc21Record extends MarcRecord {
  protected static final List<String> MARC21_SUBJECT_TAGS = Arrays.asList(
    "052", "055", "072", "080", "082", "083", "084", "085", "086",
    "600", "610", "611", "630", "647", "648", "650", "651",
    "653", "654", "655", "656", "657", "658", "662"
  );

  private static final Pattern positionalPattern = Pattern.compile("^(Leader|00[678])/(.*)$");
  private static final List<String> simpleControlTags = Arrays.asList("001", "003", "005");
  protected static final List<String> allowedControlFieldTags = Arrays.asList("001", "003", "005", "006", "007", "008");

  private final List<Control006> control006 = new ArrayList<>();
  private final List<Control007> control007 = new ArrayList<>();

  protected MarcPositionalControlField control008;

  public Marc21Record(String id) {
    super(id);
  }

  public Marc21Record() {
    super();
  }

  @Override
  public String getId() {
    if (id != null)
      return id;
    return control001 != null ? control001.getContent() : null;
  }

  @Override
  public List<MarcControlField> getControlfields() {
    List<MarcControlField> list = super.getControlfields();
    if (control006 != null && !control006.isEmpty())
      list.addAll(control006);
    if (control007 != null && !control007.isEmpty())
      list.addAll(control007);
    if (control008 != null)
      list.add(control008);
    return list;
  }

  public List<MarcPositionalControlField> getPositionalControlfields() {
    List<MarcPositionalControlField> list = new ArrayList<>();
    if (control006 != null && !control006.isEmpty())
      list.addAll(control006);
    if (control007 != null && !control007.isEmpty())
      list.addAll(control007);
    if (control008 != null)
      list.add(control008);
    return list;
  }


  public List<Control006> getControl006() {
    return control006;
  }

  public void setControl006(Control006 control006) {
    this.control006.add(control006);
    control006.setMarcRecord(this);
    controlfieldIndex.put(control006.getDefinition().getTag(), (List) this.control006);
  }

  public List<Control007> getControl007() {
    return control007;
  }

  public void setControl007(Control007 control007) {
    this.control007.add(control007);
    control007.setMarcRecord(this);
    controlfieldIndex.put(control007.getDefinition().getTag(), (List) this.control007);
  }

  public MarcPositionalControlField getControl008() {
    return control008;
  }

  public void setControl008(MarcPositionalControlField control008) {
    this.control008 = control008;
    control008.setMarcRecord(this);
    controlfieldIndex.put(control008.getDefinition().getTag(), Arrays.asList(control008));
  }

  @Override
  public Map<String, List<String>> getKeyValuePairs(SolrFieldType type,
                                                    boolean withDeduplication,
                                                    MarcVersion marcVersion) {
    if (mainKeyValuePairs == null) {
      mainKeyValuePairs = new LinkedHashMap<>();

      if (!schemaType.equals(SchemaType.PICA)) {
        mainKeyValuePairs.put("type", Arrays.asList(getType().getValue()));
        mainKeyValuePairs.putAll(leader.getKeyValuePairs(type));
      }

      for (MarcControlField controlField : getControlfields())
        if (controlField != null)
          mainKeyValuePairs.putAll(controlField.getKeyValuePairs(type));

      getKeyValuePairsForDatafields(type, withDeduplication, marcVersion);
    }

    return mainKeyValuePairs;
  }

  @Override
  public String asJson() {
    ObjectMapper mapper = new ObjectMapper();

    Map<String, Object> map = new LinkedHashMap<>();
    if (!schemaType.equals(SchemaType.PICA))
      map.put("leader", leader.getContent());

    for (MarcControlField field : getControlfields()) {
      if (field != null) {
        map.put(field.getDefinition().getTag(), field.getContent());
      }
    }

    datafieldsAsJson(map);
    return transformMapToJson(mapper, map);
  }

  @Override
  public List<String> search(String path, String query) {
    List<String> results = new ArrayList<>();
    if (path.equals("001") || path.equals("003") || path.equals("005")) {
      searchControlField(path, query, results);
      return results;
    }
    if (path.startsWith("006")) {
      for (Control006 instance : control006)
        searchPositionalControlField(instance, path, query, results);
      return results;
    }

    if (path.startsWith("007")) {
      for (Control007 instance : control007)
        searchPositionalControlField(instance, path, query, results);
      return results;
    }

    if (path.startsWith("008")) {
      searchPositionalControlField(control008, path, query, results);
      return results;
    }

    Matcher matcher = dataFieldPattern.matcher(path);
    if (matcher.matches()) {
      String tag = matcher.group(1);
      String subfieldCode = matcher.group(2);
      if (datafieldIndex.containsKey(tag)) {
        for (DataField field : datafieldIndex.get(tag)) {
          if (searchDatafield(query, results, subfieldCode, field)) break;
        }
      }
    }

    matcher = positionalPattern.matcher(path);
    if (matcher.matches()) {
      searchByPosition(query, results, matcher);
    }
    return results;
  }

  protected List<String> getSubjectTags() {
    return MARC21_SUBJECT_TAGS;
  }

  private void searchControlField(String path, String query, List<String> results) {
    MarcControlField controlField = null;
    switch (path) {
      case "001": controlField = control001; break;
      case "003": controlField = control003; break;
      case "005": controlField = control005; break;
      default: break;
    }
    if (controlField != null && controlField.getContent().equals(query))
      results.add(controlField.getContent());
  }

  private void searchPositionalControlField(MarcPositionalControlField controlField,
                                            String path, String query, List<String> results) {
    if (controlField != null) {
      Map<ControlfieldPositionDefinition, String> map = controlField.getMap();
      for (ControlfieldPositionDefinition subfield : controlField.getMap().keySet()) {
        if (subfield.getId().equals(path)) {
          if (map.get(subfield).equals(query))
            results.add(map.get(subfield));
          break;
        }
      }
    }
  }

  private void searchByPosition(String query, List<String> results, Matcher matcher) {
    String tag = matcher.group(1);
    String position = matcher.group(2);
    int start;
    int end;
    if (position.contains("-")) {
      String[] parts = position.split("-", 2);
      start = Integer.parseInt(parts[0]);
      end = Integer.parseInt(parts[1]);
    } else {
      start = Integer.parseInt(position);
      end = start + 1;
    }
    String content = null;
    if (tag.equals("Leader")) {
      content = leader.getLeaderString();
    } else {
      MarcControlField controlField = null;
      // TODO: fix it!
      switch (tag) {
        case "006": controlField = control006.get(0); break;
        case "007": controlField = control007.get(0); break;
        case "008": controlField = control008; break;
        default: break;
      }
      if (controlField != null)
        content = controlField.getContent();
    }

    if (content != null && content.substring(start, end).equals(query)) {
      results.add(content.substring(start, end));
    }
  }

  @Override
  public List<String> select(MarcSpec selector) {
    List<String> results = new ArrayList<>();
    if (selector.getFieldTag().equals("LDR") && leader != null && StringUtils.isNotEmpty(leader.getContent())) {
      return selectLeader(selector);
    }

    if (controlfieldIndex.containsKey(selector.getFieldTag())) {
      return selectControlFields(selector);
    }

    if (datafieldIndex.containsKey(selector.getFieldTag())) {
      return selectDatafields(selector);
    }

    if (selector.getFieldTag().equals("008") && control008 != null) {
      return selectControl008(selector);
    }
    return results;
  }

  private List<String> selectLeader(MarcSpec selector) {
    List<String> selectedResults = new ArrayList<>();
    if (selector.hasRangeSelector()) {
      selectedResults.add(selector.selectRange(leader.getContent()));
    } else {
      selectedResults.add(leader.getContent());
    }
    return selectedResults;
  }

  private List<String> selectControlFields(MarcSpec selector) {
    List<String> selectedResults = new ArrayList<>();
    for (MarcControlField controlField : controlfieldIndex.get(selector.getFieldTag())) {
      if (controlField == null) {
        continue;
      }
      if (!simpleControlTags.contains(controlField.getDefinition().getTag())) {
        // TODO: check control subfields
      }
      if (selector.hasRangeSelector()) {
        selectedResults.add(selector.selectRange(controlField.getContent()));
      } else {
        selectedResults.add(controlField.getContent());
      }
    }
    return selectedResults;
  }

  private List<String> selectDatafields(MarcSpec selector) {
    List<String> selectedResults = new ArrayList<>();

    List<DataField> selectedDatafields = datafieldIndex.get(selector.getFieldTag());

    for (DataField field : selectedDatafields) {
      List<String> selectedFromDatafield = selectDatafield(field, selector);
      selectedResults.addAll(selectedFromDatafield);
    }

    return selectedResults;
  }

  private List<String> selectControl008(MarcSpec selector) {
    // I don't understand why this method is separate from selectControlFields as it feels like it should be covered
    // by that method.
    List<String> selectedResults = new ArrayList<>();

    if (selector.getCharStart() != null) {
      ControlfieldPositionDefinition definition = control008.getSubfieldByPosition(selector.getCharStart());
      selectedResults.add(control008.getMap().get(definition));
    } else {
      selectedResults.add(control008.getContent());
    }

    return selectedResults;
  }

  @Override
  public List<String> getAllowedControlFieldTags() {
    return allowedControlFieldTags;
  }

  @Override
  public Map<ShelfReadyFieldsBooks, Map<String, List<String>>> getShelfReadyMap() {
    return Collections.emptyMap();
  }

  @Override
  public void setField(String tag, String content, MarcVersion marcVersion) {
    if (marcVersion.equals(MarcVersion.UNIMARC)) {
      content = UnimarcConverter.contentFromUnimarc(tag, content);
      tag = UnimarcConverter.tagFromUnimarc(tag);
    }

    switch (tag) {
      case "001":
        setControl001(new Control001(content));
        break;
      case "003":
        setControl003(new Control003(content));
        break;
      case "005":
        setControl005(new Control005(content, this));
        break;
      case "006":
        setControl006(new Control006(content, this));
        break;
      case "007":
        setControl007(new Control007(content, this));
        break;
      case "008":
        setControl008(new Control008(content, this));
        break;
      default:
        DataFieldDefinition definition = MarcFactory.getDataFieldDefinition(tag, marcVersion);
        if (definition == null) {
          addUnhandledTags(tag);
        }

        DataField dataField = new DataField(tag, content, marcVersion);
        addDataField(dataField);
        break;
    }

  }

  @Override
  public void setField(String tag, String ind1, String ind2, String content, MarcVersion marcVersion) {

    switch (tag) {
      case "001":
        setControl001(new Control001(content));
        break;
      case "003":
        setControl003(new Control003(content));
        break;
      case "005":
        setControl005(new Control005(content, this));
        break;
      case "006":
        setControl006(new Control006(content, this));
        break;
      case "007":
        setControl007(new Control007(content, this));
        break;
      case "008":
        setControl008(new Control008(content, this));
        break;
      default:
        DataFieldDefinition definition = MarcFactory.getDataFieldDefinition(tag, marcVersion);
        if (definition == null) {
          addUnhandledTags(tag);
        }
        addDataField(new DataField(tag, ind1, ind2, content, marcVersion));
        break;
    }
  }

}
