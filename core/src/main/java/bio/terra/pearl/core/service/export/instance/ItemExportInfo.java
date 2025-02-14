package bio.terra.pearl.core.service.export.instance;

import bio.terra.pearl.core.model.survey.QuestionChoice;
import bio.terra.pearl.core.service.export.formatters.DataValueExportType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

/** An 'item' corresponds to either a survey question or a specific field of a data model */
@Getter @Builder
public class ItemExportInfo {
    @Builder.Default
    private int maxNumRepeats = 1;
    @Builder.Default
    private boolean splitOptionsIntoColumns = false;
    @Builder.Default
    private boolean stableIdsForOptions = true;
    @Builder.Default
    private boolean allowMultiple = false;
    @Builder.Default
    private boolean hasOtherDescription = false;

    // for now, we support bean property accesses, or question stableId answer access
    private String propertyAccessor;
    private String questionStableId;
    private String baseColumnKey;
    private DataValueExportType dataType;
    private String questionType;
    private String questionText;
    @Builder.Default
    private List<QuestionChoice> choices = null;
    /**
     * for survey questions, we need to have a map of past versions so we can look up values of stableIds that may
     * no longer be supported, and also to produce a full data dictionary
     */
    @Builder.Default
    private Map<Integer, ItemExportInfo> versionMap = new HashMap<>();
}
