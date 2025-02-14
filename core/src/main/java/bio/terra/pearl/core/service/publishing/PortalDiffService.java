package bio.terra.pearl.core.service.publishing;

import bio.terra.pearl.core.dao.publishing.PortalEnvironmentChangeRecordDao;
import bio.terra.pearl.core.model.BaseEntity;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.Versioned;
import bio.terra.pearl.core.model.consent.ConsentForm;
import bio.terra.pearl.core.model.consent.StudyEnvironmentConsent;
import bio.terra.pearl.core.model.notification.EmailTemplate;
import bio.terra.pearl.core.model.notification.NotificationConfig;
import bio.terra.pearl.core.model.portal.PortalEnvironment;
import bio.terra.pearl.core.model.publishing.*;
import bio.terra.pearl.core.model.site.SiteContent;
import bio.terra.pearl.core.model.study.Study;
import bio.terra.pearl.core.model.study.StudyEnvironment;
import bio.terra.pearl.core.model.survey.StudyEnvironmentSurvey;
import bio.terra.pearl.core.model.survey.Survey;
import bio.terra.pearl.core.service.notification.NotificationConfigService;
import bio.terra.pearl.core.service.portal.PortalEnvironmentConfigService;
import bio.terra.pearl.core.service.portal.PortalEnvironmentService;
import bio.terra.pearl.core.service.site.SiteContentService;
import bio.terra.pearl.core.service.study.StudyEnvironmentService;
import bio.terra.pearl.core.service.study.StudyService;
import bio.terra.pearl.core.service.survey.SurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PortalDiffService {
    public static final List<String> CONFIG_IGNORE_PROPS = List.of("id", "createdAt", "lastUpdatedAt", "class",
            "studyEnvironmentId", "portalEnvironmentId", "emailTemplateId", "emailTemplate",
            "consentFormId", "consentForm", "surveyId", "survey", "versionedEntity");
    private PortalEnvironmentService portalEnvService;
    private PortalEnvironmentConfigService portalEnvironmentConfigService;
    private SiteContentService siteContentService;
    private SurveyService surveyService;
    private NotificationConfigService notificationConfigService;
    private ObjectMapper objectMapper;
    private PortalEnvironmentChangeRecordDao portalEnvironmentChangeRecordDao;
    private StudyEnvironmentService studyEnvironmentService;
    private StudyService studyService;

    public PortalDiffService(PortalEnvironmentService portalEnvService,
                             PortalEnvironmentConfigService portalEnvironmentConfigService,
                             SiteContentService siteContentService, SurveyService surveyService,
                             NotificationConfigService notificationConfigService,
                             ObjectMapper objectMapper,
                             PortalEnvironmentChangeRecordDao portalEnvironmentChangeRecordDao,
                             StudyEnvironmentService studyEnvironmentService, StudyService studyService) {
        this.portalEnvService = portalEnvService;
        this.portalEnvironmentConfigService = portalEnvironmentConfigService;
        this.siteContentService = siteContentService;
        this.surveyService = surveyService;
        this.notificationConfigService = notificationConfigService;
        this.objectMapper = objectMapper;
        this.portalEnvironmentChangeRecordDao = portalEnvironmentChangeRecordDao;
        this.studyEnvironmentService = studyEnvironmentService;
        this.studyService = studyService;
    }

    public PortalEnvironmentChange diffPortalEnvs(String shortcode, EnvironmentName source, EnvironmentName dest) throws Exception {
        PortalEnvironment sourceEnv = loadPortalEnvForProcessing(shortcode, source);
        PortalEnvironment destEnv = loadPortalEnvForProcessing(shortcode, dest);
        return diffPortalEnvs(sourceEnv, destEnv);
    }

    public PortalEnvironmentChange diffPortalEnvs(PortalEnvironment sourceEnv, PortalEnvironment destEnv) throws Exception {
        var preRegRecord = new VersionedEntityChange<Survey>(sourceEnv.getPreRegSurvey(), destEnv.getPreRegSurvey());
        var siteContentRecord = new VersionedEntityChange<SiteContent>(sourceEnv.getSiteContent(), destEnv.getSiteContent());
        var envConfigChanges = ConfigChange.allChanges(sourceEnv.getPortalEnvironmentConfig(),
                destEnv.getPortalEnvironmentConfig(), CONFIG_IGNORE_PROPS);
        ListChange<NotificationConfig, VersionedConfigChange<EmailTemplate>> notificationConfigChanges = diffConfigLists(sourceEnv.getNotificationConfigs(),
                destEnv.getNotificationConfigs(),
                CONFIG_IGNORE_PROPS);

        List<StudyEnvironmentChange> studyEnvChanges = new ArrayList<>();
        List<Study> studies = studyService.findByPortalId(sourceEnv.getPortalId());
        for (Study study : studies) {
            StudyEnvironmentChange studyEnvChange = diffStudyEnvs(study.getShortcode(), sourceEnv.getEnvironmentName(), destEnv.getEnvironmentName());
            studyEnvChanges.add(studyEnvChange);
        }

        return new PortalEnvironmentChange(
                siteContentRecord,
                envConfigChanges,
                preRegRecord,
                notificationConfigChanges,
                studyEnvChanges
        );
    }

    protected PortalEnvironment loadPortalEnvForProcessing(String shortcode, EnvironmentName envName) {
        PortalEnvironment portalEnv = portalEnvService.findOne(shortcode, envName).get();
        if (portalEnv.getPortalEnvironmentConfigId() != null) {
            portalEnv.setPortalEnvironmentConfig(portalEnvironmentConfigService
                    .find(portalEnv.getPortalEnvironmentConfigId()).get());
        }
        if (portalEnv.getSiteContentId() != null) {
            portalEnv.setSiteContent(siteContentService.find(portalEnv.getSiteContentId()).get());
        }
        if (portalEnv.getPreRegSurveyId() != null) {
            portalEnv.setPreRegSurvey(surveyService.find(portalEnv.getPreRegSurveyId()).get());
        }
        var notificationConfigs = notificationConfigService.findByPortalEnvironmentId(portalEnv.getId());
        notificationConfigService.attachTemplates(notificationConfigs);
        portalEnv.setNotificationConfigs(notificationConfigs);

        return portalEnv;
    }

    public static <C extends VersionedEntityConfig, T extends BaseEntity & Versioned> ListChange<C, VersionedConfigChange<T>> diffConfigLists(
            List<C> sourceConfigs,
            List<C> destConfigs,
            List<String> ignoreProps)
    throws Exception {
        List<C> unmatchedDestConfigs = new ArrayList<>(destConfigs);
        List<VersionedConfigChange<T>> changedRecords = new ArrayList<>();
        List<C> addedConfigs = new ArrayList<>();
        for (C sourceConfig : sourceConfigs) {
            var matchedConfig = unmatchedDestConfigs.stream().filter(
                    destConfig -> isVersionedConfigMatch(sourceConfig, destConfig))
                    .findAny().orElse(null);
            if (matchedConfig == null) {
                addedConfigs.add(sourceConfig);
            } else {
                // this remove only works if the config has an ID, since that's how BaseEntity equality works
                // that's fine, since we're only working with already-persisted entities in this list.
                unmatchedDestConfigs.remove(matchedConfig);
                var changeRecord = new VersionedConfigChange<T>(
                        sourceConfig.getId(), matchedConfig.getId(),
                        ConfigChange.allChanges(sourceConfig, matchedConfig, ignoreProps),
                        new VersionedEntityChange<T>(sourceConfig.versionedEntity(), matchedConfig.versionedEntity())
                );
                if (changeRecord.isChanged()) {
                    changedRecords.add(changeRecord);
                }

            }
        }
        return new ListChange<>(addedConfigs, unmatchedDestConfigs, changedRecords);
    }

    /** for now, just checks to see if they reference the same versioned document */
    public static boolean isVersionedConfigMatch(VersionedEntityConfig configA, VersionedEntityConfig configB) {
        if (configA == null || configB == null) {
            return configA == configB;
        }
        if (configA.versionedEntity() == null || configB.versionedEntity() == null) {
            return false;
        }
        return Objects.equals(configA.versionedEntity().getStableId(), configB.versionedEntity().getStableId());
    }

    public StudyEnvironmentChange diffStudyEnvs(String studyShortcode, EnvironmentName source, EnvironmentName dest) throws Exception {
        StudyEnvironment sourceEnv = loadStudyEnvForProcessing(studyShortcode, source);
        StudyEnvironment destEnv = loadStudyEnvForProcessing(studyShortcode, dest);
        return diffStudyEnvs(studyShortcode, sourceEnv, destEnv);
    }

    public StudyEnvironmentChange diffStudyEnvs(String studyShortcode, StudyEnvironment sourceEnv, StudyEnvironment destEnv) throws Exception {
        var envConfigChanges = ConfigChange.allChanges(
                sourceEnv.getStudyEnvironmentConfig(),
                destEnv.getStudyEnvironmentConfig(),
                CONFIG_IGNORE_PROPS);
        var preEnrollChange = new VersionedEntityChange<Survey>(sourceEnv.getPreEnrollSurvey(), destEnv.getPreEnrollSurvey());
        ListChange<StudyEnvironmentConsent, VersionedConfigChange<ConsentForm>> consentChanges = diffConfigLists(
                sourceEnv.getConfiguredConsents(),
                destEnv.getConfiguredConsents(),
                CONFIG_IGNORE_PROPS);
        ListChange<StudyEnvironmentSurvey, VersionedConfigChange<Survey>> surveyChanges = diffConfigLists(
                sourceEnv.getConfiguredSurveys(),
                destEnv.getConfiguredSurveys(),
                CONFIG_IGNORE_PROPS);
        ListChange<NotificationConfig, VersionedConfigChange<EmailTemplate>> notificationConfigChanges = diffConfigLists(
                sourceEnv.getNotificationConfigs(),
                destEnv.getNotificationConfigs(),
                CONFIG_IGNORE_PROPS);

        return new StudyEnvironmentChange(
                studyShortcode,
                envConfigChanges,
                preEnrollChange,
                consentChanges,
                surveyChanges,
                notificationConfigChanges
        );
    }

    public StudyEnvironment loadStudyEnvForProcessing(String shortcode, EnvironmentName envName) {
        StudyEnvironment studyEnvironment = studyEnvironmentService.findByStudy(shortcode, envName).get();
        return studyEnvironmentService.loadWithAllContent(studyEnvironment);
    }


}
