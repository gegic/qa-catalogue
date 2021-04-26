package de.gwdg.metadataqa.marc.definition.controlpositions.tag007;

import de.gwdg.metadataqa.marc.Utils;
import de.gwdg.metadataqa.marc.definition.structure.ControlfieldPositionDefinition;

/**
 * Category of material
 * https://www.loc.gov/marc/bibliographic/bd007c.html
 */
public class Tag007common00 extends ControlfieldPositionDefinition {
  private static Tag007common00 uniqueInstance;

  private Tag007common00() {
    initialize();
    extractValidCodes();
  }

  public static Tag007common00 getInstance() {
    if (uniqueInstance == null)
      uniqueInstance = new Tag007common00();
    return uniqueInstance;
  }

  private void initialize() {
    label = "Category of material";
    id = "007common00";
    mqTag = "categoryOfMaterial";
    positionStart = 0;
    positionEnd = 1;
    descriptionUrl = "https://www.loc.gov/marc/bibliographic/bd007c.html";
    codes = Utils.generateCodes(
      "c", "Electronic resource"
    );
  }
}