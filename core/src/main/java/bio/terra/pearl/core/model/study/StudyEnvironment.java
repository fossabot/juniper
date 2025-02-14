package bio.terra.pearl.core.model.study;

import bio.terra.pearl.core.model.BaseEntity;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.consent.StudyEnvironmentConsent;
import bio.terra.pearl.core.model.notification.NotificationConfig;
import bio.terra.pearl.core.model.survey.StudyEnvironmentSurvey;
import bio.terra.pearl.core.model.survey.Survey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter @SuperBuilder
@NoArgsConstructor
public class StudyEnvironment extends BaseEntity {
    private EnvironmentName environmentName;
    private UUID studyId;

    private UUID studyEnvironmentConfigId;
    private StudyEnvironmentConfig studyEnvironmentConfig;
    private UUID preEnrollSurveyId;
    private Survey preEnrollSurvey;
    @Builder.Default
    private List<StudyEnvironmentSurvey> configuredSurveys = new ArrayList<>();
    @Builder.Default
    private List<StudyEnvironmentConsent> configuredConsents = new ArrayList<>();
    @Builder.Default
    private List<NotificationConfig> notificationConfigs = new ArrayList<>();
}
