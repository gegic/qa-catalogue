package de.gwdg.metadataqa.marc.cli.plugin;

import de.gwdg.metadataqa.marc.cli.parameters.CompletenessParameters;

public class CompletenessFactory {

  private CompletenessFactory() {}

  public static CompletenessPlugin create(CompletenessParameters parameters) {
    // TODO this will have to be addressed with the completeness analysis
    // I suppose that the Marc21CompletenessPlugin could be used for UNIMARC as well, at least for now, until
    // I am completely sure what comprises a complete UNIMARC record.
    if (parameters.isMarc21() || parameters.isUnimarc()) {
      return new Marc21CompletenessPlugin(parameters);
    } else if (parameters.isPica()) {
      return new PicaCompletenessPlugin(parameters);
    }
    return null;
  }

}
