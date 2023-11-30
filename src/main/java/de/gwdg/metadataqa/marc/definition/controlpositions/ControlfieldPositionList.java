package de.gwdg.metadataqa.marc.definition.controlpositions;

import de.gwdg.metadataqa.marc.definition.structure.ControlfieldPositionDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ControlfieldPositionList {

  protected Map<String, List<ControlfieldPositionDefinition>> positions = new TreeMap<>();
  protected Map<String, ControlfieldPositionDefinition> positionIdMap = new HashMap<>();

  public Map<String, List<ControlfieldPositionDefinition>> getPositions() {
    return positions;
  }

  public List<ControlfieldPositionDefinition> get(String category) {
    return positions.get(category);
  }

  protected void index() {
    for (List<ControlfieldPositionDefinition> positionDefinitions : positions.values()) {
      for (ControlfieldPositionDefinition position : positionDefinitions) {
        positionIdMap.put(position.getId(), position);
      }
    }
  }

  public ControlfieldPositionDefinition getById(String id) {
    return positionIdMap.getOrDefault(id, null);
  }

  public void setPositions(List<ControlfieldPositionDefinition> positions) {
    // TODO I'm not really sure what categories are supposed to be, so in this case everything is put into the same category
    // Also I'm not going to be using Control008Type. Instead a simple string ALL_MATERIALS will be used for now.
    if (!this.positions.containsKey("ALL_MATERIALS")) {
      this.positions.put("ALL_MATERIALS", new ArrayList<>());
    }

    for (ControlfieldPositionDefinition position : positions) {
      this.positions.get("ALL_MATERIALS").add(position);
    }

    index();
  }
}
