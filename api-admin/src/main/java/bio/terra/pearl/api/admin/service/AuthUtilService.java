package bio.terra.pearl.api.admin.service;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.pearl.core.model.admin.AdminUser;
import bio.terra.pearl.core.model.participant.Enrollee;
import bio.terra.pearl.core.model.portal.Portal;
import bio.terra.pearl.core.model.study.PortalStudy;
import bio.terra.pearl.core.model.study.StudyEnvironment;
import bio.terra.pearl.core.model.survey.Survey;
import bio.terra.pearl.core.service.admin.AdminUserService;
import bio.terra.pearl.core.service.exception.NotFoundException;
import bio.terra.pearl.core.service.exception.PermissionDeniedException;
import bio.terra.pearl.core.service.participant.EnrolleeService;
import bio.terra.pearl.core.service.portal.PortalService;
import bio.terra.pearl.core.service.study.PortalStudyService;
import bio.terra.pearl.core.service.survey.SurveyService;
import com.auth0.jwt.JWT;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/** Utility service for common auth-related methods */
@Service
public class AuthUtilService {
  private AdminUserService adminUserService;
  private BearerTokenFactory bearerTokenFactory;
  private PortalService portalService;
  private PortalStudyService portalStudyService;
  private EnrolleeService enrolleeService;
  private SurveyService surveyService;

  public AuthUtilService(
      AdminUserService adminUserService,
      BearerTokenFactory bearerTokenFactory,
      PortalService portalService,
      PortalStudyService portalStudyService,
      EnrolleeService enrolleeService,
      SurveyService surveyService) {
    this.adminUserService = adminUserService;
    this.bearerTokenFactory = bearerTokenFactory;
    this.portalService = portalService;
    this.portalStudyService = portalStudyService;
    this.enrolleeService = enrolleeService;
    this.surveyService = surveyService;
  }

  /** gets the user from the request, throwing an exception if not present */
  public AdminUser requireAdminUser(HttpServletRequest request) {
    String token = bearerTokenFactory.from(request).getToken();
    var decodedJWT = JWT.decode(token);
    var email = decodedJWT.getClaim("email").asString();
    Optional<AdminUser> userOpt = adminUserService.findByUsername(email);
    if (userOpt.isEmpty()) {
      throw new UnauthorizedException("User not found: " + email);
    }
    return userOpt.get();
  }

  /**
   * this will throw not found if the portal doesn't exist or if the user does't have permission, to
   * avoid leaking information
   */
  public Portal authUserToPortal(AdminUser user, String portalShortcode) {
    Optional<Portal> portalOpt = portalService.findOneByShortcode(portalShortcode);
    if (portalOpt.isPresent()) {
      Portal portal = portalOpt.get();
      if (portalService.checkAdminIsInPortal(user, portal.getId())) {
        return portal;
      }
    }
    throw new NotFoundException("Portal %s not found".formatted(portalShortcode));
  }

  public PortalStudy authUserToStudy(
      AdminUser user, String portalShortcode, String studyShortcode) {
    Portal portal = authUserToPortal(user, portalShortcode);
    Optional<PortalStudy> portalStudy =
        portalStudyService.findStudyInPortal(studyShortcode, portal.getId());
    if (portalStudy.isEmpty()) {
      throw new PermissionDeniedException(
          "User %s does not have permissions on study %s"
              .formatted(user.getUsername(), studyShortcode));
    }
    return portalStudy.get();
  }

  public void checkEnrolleeInStudyEnv(Enrollee enrollee, StudyEnvironment studyEnvironment) {
    if (!studyEnvironment.getId().equals(enrollee.getStudyEnvironmentId())) {
      throw new PermissionDeniedException(
          "Enrollee %s not accessible from study environment".formatted(enrollee.getShortcode()));
    }
  }

  /**
   * returns the enrollee if the user is authorized to access/modify it, throws an error otherwise
   */
  public Enrollee authAdminUserToEnrollee(AdminUser user, String enrolleeShortcode) {
    // find what portal(s) the enrollee is in, and then check that the adminUser is authorized in at
    // least one
    List<PortalStudy> portalStudies = portalStudyService.findByEnrollee(enrolleeShortcode);
    List<UUID> portalIds = portalStudies.stream().map(PortalStudy::getPortalId).toList();
    if (!portalService.checkAdminInAtLeastOnePortal(user, portalIds)) {
      throw new PermissionDeniedException(
          "User %s does not have permissions on enrollee %s or enrollee does not exist"
              .formatted(user.getUsername(), enrolleeShortcode));
    }
    return enrolleeService.findOneByShortcode(enrolleeShortcode).get();
  }

  /** confirms that the Survey is accessible from the given portal */
  public Survey authSurveyToPortal(Portal portal, String stableId, int version) {
    Optional<Survey> surveyOpt = surveyService.findByStableId(stableId, version);
    return authSurveyToPortal(portal, surveyOpt);
  }

  /** confirms that the Survey is accessible from the given portal */
  public Survey authSurveyToPortal(Portal portal, UUID surveyId) {
    Optional<Survey> surveyOpt = surveyService.find(surveyId);
    return authSurveyToPortal(portal, surveyOpt);
  }

  protected Survey authSurveyToPortal(Portal portal, Optional<Survey> surveyOpt) {
    if (surveyOpt.isEmpty()) {
      throw new NotFoundException("No such survey exists in " + portal.getName());
    }
    Survey survey = surveyOpt.get();
    if (!portal.getId().equals(survey.getPortalId())) {
      throw new NotFoundException("No such survey exists in " + portal.getName());
    }
    return survey;
  }
}
