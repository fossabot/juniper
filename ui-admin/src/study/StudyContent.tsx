import React, { useContext, useState } from 'react'

import { StudyEnvContextT } from './StudyEnvironmentRouter'
import { Link } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faPlus } from '@fortawesome/free-solid-svg-icons/faPlus'
import CreateSurveyModal from './surveys/CreateSurveyModal'
import { faEllipsisH } from '@fortawesome/free-solid-svg-icons'
import ArchiveSurveyModal from './surveys/ArchiveSurveyModal'
import DeleteSurveyModal from './surveys/DeleteSurveyModal'
import { StudyEnvironmentSurvey } from '@juniper/ui-core'
import CreateConsentModal from './consents/CreateConsentModal'
import { Button, IconButton } from 'components/forms/Button'
import CreatePreEnrollSurveyModal from './surveys/CreatePreEnrollSurveyModal'
import { renderPageHeader } from 'util/pageUtils'
import Api from 'api/api'
import { PortalContext, PortalContextT } from 'portal/PortalProvider'
import LoadingSpinner from 'util/LoadingSpinner'

/** renders the main configuration page for a study environment */
function StudyContent({ studyEnvContext }: {studyEnvContext: StudyEnvContextT}) {
  const { currentEnv } = studyEnvContext
  const portalContext = useContext(PortalContext) as PortalContextT

  const contentHeaderStyle = {
    paddingTop: '1em',
    borderBottom: '1px solid #f6f6f6'
  }

  const [isLoading, setIsLoading] = useState(false)
  const preEnrollSurvey = currentEnv.preEnrollSurvey
  const isReadOnlyEnv = !(currentEnv.environmentName === 'sandbox')
  const [showCreateSurveyModal, setShowCreateSurveyModal] = useState(false)
  const [showCreateConsentModal, setShowCreateConsentModal] = useState(false)
  const [showArchiveSurveyModal, setShowArchiveSurveyModal] = useState(false)
  const [showDeleteSurveyModal, setShowDeleteSurveyModal] = useState(false)
  const [showCreatePreEnrollSurveyModal, setShowCreatePreEnrollModal] = useState(false)
  const [selectedSurveyConfig, setSelectedSurveyConfig] = useState<StudyEnvironmentSurvey>()

  currentEnv.configuredSurveys
    .sort((a, b) => a.surveyOrder - b.surveyOrder)
  currentEnv.configuredConsents
    .sort((a, b) => a.consentOrder - b.consentOrder)

  const updateConfiguredSurvey = async (surveyConfig: StudyEnvironmentSurvey) => {
    setIsLoading(true)
    await Api.updateConfiguredSurvey(studyEnvContext.portal.shortcode,
      studyEnvContext.study.shortcode, currentEnv.environmentName, surveyConfig)
    await portalContext.reloadPortal(studyEnvContext.portal.shortcode)
    setIsLoading(false)
  }

  return <div className="container-fluid px-4 py-2">
    { renderPageHeader('Forms & Surveys') }
    <LoadingSpinner isLoading={isLoading}>
      <div className="col-12">
        { currentEnv.studyEnvironmentConfig.initialized && <ul className="list-unstyled">
          <li className="mb-3 bg-white">
            <div style={contentHeaderStyle}>
              <h6>Pre-enrollment questionnaire</h6>
            </div>
            <div className="flex-grow-1 pt-3">
              { preEnrollSurvey && <ul className="list-unstyled">
                <li className="d-flex align-items-center">
                  <Link to={`preEnroll/${preEnrollSurvey.stableId}?readOnly=${isReadOnlyEnv}`}>
                    {preEnrollSurvey.name} <span className="detail">v{preEnrollSurvey.version}</span>
                  </Link>

                  { !isReadOnlyEnv && <div className="nav-item dropdown ms-1">
                    <IconButton icon={faEllipsisH}  data-bs-toggle="dropdown"
                      aria-expanded="false" aria-label="configure pre-enroll menu"/>
                    <div className="dropdown-menu">
                      <ul className="list-unstyled">
                        <li>
                          <button className="dropdown-item"
                            onClick={() => alert('To remove a pre-enroll survey, contact support')}>
                            Remove
                          </button>
                        </li>
                      </ul>
                    </div>
                  </div> }
                </li>
              </ul>}
              { (!preEnrollSurvey && !isReadOnlyEnv) && <Button variant="secondary"
                data-testid={'addPreEnroll'}
                onClick={() => {
                  setShowCreatePreEnrollModal(!showCreatePreEnrollSurveyModal)
                }}>
                <FontAwesomeIcon icon={faPlus}/> Add
              </Button>
              }
            </div>
          </li>
          <li className="mb-3 bg-white">
            <div style={contentHeaderStyle}>
              <h6>Consent forms</h6>
            </div>
            <div className="flex-grow-1 pt-3">
              <ul className="list-unstyled">
                { currentEnv.configuredConsents.map((config, index) => {
                  const consentForm = config.consentForm
                  return <li key={index}>
                    <Link to={`consentForms/${consentForm.stableId}?readOnly=${isReadOnlyEnv}`}>
                      {consentForm.name} <span className="detail">v{consentForm.version}</span>
                    </Link>
                  </li>
                }) }
                <li>
                  { !isReadOnlyEnv && <button className="btn btn-secondary" data-testid={'addConsent'} onClick={() => {
                    setShowCreateConsentModal(!showCreateConsentModal)
                  }}>
                    <FontAwesomeIcon icon={faPlus}/> Add
                  </button> }
                </li>
              </ul>
            </div>
          </li>
          <li className="mb-3 bg-white">
            <div style={contentHeaderStyle}>
              <h6>Surveys</h6>
            </div>
            <div className="flex-grow-1 pt-3">
              <ul className="list-unstyled">
                { currentEnv.configuredSurveys.map((surveyConfig, index) => {
                  const survey = surveyConfig.survey
                  return (
                    <li className="p-1 d-flex align-items-center" key={index}>
                      <div className="d-flex align-items-center">
                        <Link to={`surveys/${survey.stableId}?readOnly=${isReadOnlyEnv}`}>
                          {survey.name}
                          <span className="mx-1 detail">v{survey.version}</span>
                          {surveyConfig.required && <span className="detail">(required)</span>}
                        </Link>
                      </div>
                      { !isReadOnlyEnv && <div className="nav-item dropdown ms-1">
                        <IconButton icon={faEllipsisH}  data-bs-toggle="dropdown"
                          aria-expanded="false" aria-label="configure survey menu"/>
                        <div className="dropdown-menu">
                          <ul className="list-unstyled">
                            <li>
                              <button className="dropdown-item"
                                onClick={() => {
                                  setShowArchiveSurveyModal(!showArchiveSurveyModal)
                                  setSelectedSurveyConfig(surveyConfig)
                                }}>
                                Archive
                              </button>
                            </li>
                            <li className="pt-2">
                              <button className="dropdown-item"
                                onClick={() => {
                                  setShowDeleteSurveyModal(!showDeleteSurveyModal)
                                  setSelectedSurveyConfig(surveyConfig)
                                }}>
                                Delete
                              </button>
                            </li>
                            <li className="pt-2">
                              <button className="dropdown-item"
                                onClick={async () => {
                                  await updateConfiguredSurvey({
                                    ...surveyConfig,
                                    required: !surveyConfig.required
                                  })
                                }}>
                                { surveyConfig.required? 'Set as optional' : 'Set as required' }
                              </button>
                            </li>
                          </ul>
                        </div>
                      </div> }
                    </li>
                  )
                })}
                {!isReadOnlyEnv && <li>
                  <Button variant="secondary" data-testid={'addSurvey'} onClick={() => {
                    setShowCreateSurveyModal(!showCreateSurveyModal)
                  }}>
                    <FontAwesomeIcon icon={faPlus}/> Add
                  </Button>
                </li> }
              </ul>
            </div>
          </li>
        </ul> }
        { showCreateSurveyModal && <CreateSurveyModal studyEnvContext={studyEnvContext}
          onDismiss={() => setShowCreateSurveyModal(false)}/> }
        { (showArchiveSurveyModal && selectedSurveyConfig) && <ArchiveSurveyModal studyEnvContext={studyEnvContext}
          selectedSurveyConfig={selectedSurveyConfig}
          onDismiss={() => setShowArchiveSurveyModal(false)}/> }
        { (showDeleteSurveyModal && selectedSurveyConfig) && <DeleteSurveyModal studyEnvContext={studyEnvContext}
          selectedSurveyConfig={selectedSurveyConfig}
          onDismiss={() => setShowDeleteSurveyModal(false)}/> }
        { showCreateConsentModal && <CreateConsentModal studyEnvContext={studyEnvContext}
          onDismiss={() => setShowCreateConsentModal(false)}/>}
        { showCreatePreEnrollSurveyModal && <CreatePreEnrollSurveyModal studyEnvContext={studyEnvContext}
          onDismiss={() => setShowCreatePreEnrollModal(false)}/> }
        { !currentEnv.studyEnvironmentConfig.initialized && <div>Not yet initialized</div> }
      </div>
    </LoadingSpinner>
  </div>
}

export default StudyContent
