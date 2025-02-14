package bio.terra.pearl.api.admin.service.forms;

import bio.terra.pearl.api.admin.service.AuthUtilService;
import bio.terra.pearl.core.model.BaseEntity;
import bio.terra.pearl.core.model.EnvironmentName;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.model.portal.Portal;
import bio.terra.pearl.core.model.study.StudyEnvironment;
import bio.terra.pearl.core.model.survey.StudyEnvironmentSurvey;
import bio.terra.pearl.core.model.survey.Survey;
import bio.terra.pearl.core.service.CascadeProperty;
import bio.terra.pearl.core.service.exception.NotFoundException;
import bio.terra.pearl.core.service.study.StudyEnvironmentService;
import bio.terra.pearl.core.service.study.StudyEnvironmentSurveyService;
import bio.terra.pearl.core.service.survey.SurveyService;
import java.util.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SurveyExtService {
  private AuthUtilService authUtilService;
  private SurveyService surveyService;
  private StudyEnvironmentSurveyService studyEnvironmentSurveyService;
  private StudyEnvironmentService studyEnvironmentService;

  public SurveyExtService(
      AuthUtilService authUtilService,
      SurveyService surveyService,
      StudyEnvironmentSurveyService studyEnvironmentSurveyService,
      StudyEnvironmentService studyEnvironmentService) {
    this.authUtilService = authUtilService;
    this.surveyService = surveyService;
    this.studyEnvironmentSurveyService = studyEnvironmentSurveyService;
    this.studyEnvironmentService = studyEnvironmentService;
  }

  public Survey get(String portalShortcode, String stableId, int version, AdminUser adminUser) {
    Portal portal = authUtilService.authUserToPortal(adminUser, portalShortcode);
    Survey survey = authUtilService.authSurveyToPortal(portal, stableId, version);
    surveyService.attachAnswerMappings(survey);
    return survey;
  }

  public List<Survey> listVersions(String portalShortcode, String stableId, AdminUser adminUser) {
    Portal portal = authUtilService.authUserToPortal(adminUser, portalShortcode);
    // This is used to populate the version selector in the admin UI. It's not necessary
    // to return the surveys with any content or answer mappings, the response will
    // be too large. Instead, just get the individual versions as content is needed.
    List<Survey> surveys = surveyService.findByStableIdNoContent(stableId);
    List<Survey> surveysInPortal =
        surveys.stream().filter(survey -> portal.getId().equals(survey.getPortalId())).toList();
    return surveysInPortal;
  }

  public Survey create(String portalShortcode, Survey survey, AdminUser adminUser) {
    Portal portal = authUtilService.authUserToPortal(adminUser, portalShortcode);
    List<Survey> existing = surveyService.findByStableId(survey.getStableId());
    if (existing.size() > 0) {
      throw new IllegalArgumentException("A survey with that stableId already exists");
    }
    survey.setPortalId(portal.getId());
    survey.setVersion(1);
    return surveyService.create(survey);
  }

  @Transactional
  public void delete(String portalShortcode, String surveyStableId, AdminUser adminUser) {
    Portal portal = authUtilService.authUserToPortal(adminUser, portalShortcode);
    // Find all of the versions of the specified survey that are in the specified portal
    List<Survey> existingVersions =
        surveyService.findByStableId(surveyStableId).stream()
            .filter(survey -> portal.getId().equals(survey.getPortalId()))
            .toList();

    if (existingVersions.size() == 0) {
      throw new NotFoundException("Survey not found");
    }

    List<UUID> existingVersionIds = existingVersions.stream().map(BaseEntity::getId).toList();

    // Find all of the configured surveys that use any version of the specified survey
    List<StudyEnvironmentSurvey> referencingSurveys =
        existingVersionIds.stream()
            .flatMap(
                surveyId -> {
                  List<StudyEnvironmentSurvey> configuredSurveys =
                      studyEnvironmentSurveyService.findBySurveyId(surveyId);
                  return configuredSurveys.stream();
                })
            .toList();

    // Resolve all of the study environments that contain the configured surveys
    List<StudyEnvironment> referencingStudyEnvs =
        studyEnvironmentService.findAll(
            referencingSurveys.stream()
                .map(StudyEnvironmentSurvey::getStudyEnvironmentId)
                .toList());

    if (referencingStudyEnvs.stream()
        .anyMatch(r -> !EnvironmentName.sandbox.equals(r.getEnvironmentName()))) {
      throw new IllegalArgumentException(
          "Survey is referenced by a non-sandbox environment and cannot be deleted");
    } else {
      // At this point, only sandbox environments reference the survey, so we can unlink
      // the survey from each env to prepare for full deletion.
      existingVersionIds.forEach(id -> studyEnvironmentSurveyService.deleteBySurveyId(id));

      // Delete all survey versions
      existingVersions.forEach(
          survey -> surveyService.delete(survey.getId(), CascadeProperty.EMPTY_SET));
    }
  }

  public Survey createNewVersion(String portalShortcode, Survey survey, AdminUser adminUser) {
    Portal portal = authUtilService.authUserToPortal(adminUser, portalShortcode);
    return surveyService.createNewVersion(portal.getId(), survey);
  }

  public StudyEnvironmentSurvey createConfiguredSurvey(
      String portalShortcode,
      String studyShortcode,
      EnvironmentName envName,
      StudyEnvironmentSurvey surveyToConfigure,
      AdminUser user) {
    authConfiguredSurveyRequest(portalShortcode, envName, studyShortcode, surveyToConfigure, user);
    return studyEnvironmentSurveyService.create(surveyToConfigure);
  }

  public void removeConfiguredSurvey(
      String portalShortcode,
      String studyShortcode,
      EnvironmentName envName,
      UUID configuredSurveyId,
      AdminUser user) {
    StudyEnvironmentSurvey configuredSurvey =
        studyEnvironmentSurveyService.find(configuredSurveyId).get();
    authConfiguredSurveyRequest(portalShortcode, envName, studyShortcode, configuredSurvey, user);
    studyEnvironmentSurveyService.deactivate(configuredSurveyId);
  }

  public StudyEnvironmentSurvey updateConfiguredSurvey(
      String portalShortcode,
      EnvironmentName envName,
      String studyShortcode,
      StudyEnvironmentSurvey updatedObj,
      AdminUser user) {
    authConfiguredSurveyRequest(portalShortcode, envName, studyShortcode, updatedObj, user);
    StudyEnvironmentSurvey existing = studyEnvironmentSurveyService.find(updatedObj.getId()).get();
    BeanUtils.copyProperties(updatedObj, existing);
    return studyEnvironmentSurveyService.update(existing);
  }

  /**
   * confirms the user has access to the study and that the configured survey belongs to that study,
   * and that it's in the sandbox environment. Returns the study environment for which the change is
   * being made in.
   */
  protected StudyEnvironment authConfiguredSurveyRequest(
      String portalShortcode,
      EnvironmentName envName,
      String studyShortcode,
      StudyEnvironmentSurvey updatedObj,
      AdminUser user) {
    authUtilService.authUserToStudy(user, portalShortcode, studyShortcode);
    StudyEnvironment studyEnv = studyEnvironmentService.findByStudy(studyShortcode, envName).get();
    if (!EnvironmentName.sandbox.equals(envName)) {
      throw new IllegalArgumentException(
          "Updates can only be made directly to the sandbox environment".formatted(envName));
    }
    if (!studyEnv.getId().equals(updatedObj.getStudyEnvironmentId())) {
      throw new IllegalArgumentException(
          "Study environment id in request body does not belong to this study");
    }
    return studyEnv;
  }
}
