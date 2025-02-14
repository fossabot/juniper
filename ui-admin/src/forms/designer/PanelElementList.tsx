import { faArrowRightFromBracket, faChevronDown, faChevronUp, faTimes } from '@fortawesome/free-solid-svg-icons'
import React from 'react'

import { FormElement, FormPanel } from '@juniper/ui-core'

import { IconButton } from 'components/forms/Button'

import { getElementLabel } from './designer-utils'

type PanelElementListProps = {
  readOnly: boolean
  value: FormPanel['elements']
  onChange: (newValue: FormPanel['elements'], removedElement?: FormElement) => void
}

/** UI for editing a list of form elements in a panel. */
export const PanelElementList = (props: PanelElementListProps) => {
  const { readOnly, value, onChange } = props

  return (
    <>
      <ol className="list-group list-group-numbered">
        {value.map((element, i) => {
          return (
            <li
              key={i}
              className="list-group-item d-flex align-items-center"
            >
              <div className="flex-grow-1 text-truncate ms-2">
                {getElementLabel(element)}
              </div>
              <div className="flex-shrink-0">
                <IconButton
                  aria-label="Move this element before the previous one"
                  className="ms-2"
                  disabled={readOnly || i === 0}
                  icon={faChevronUp}
                  variant="light"
                  onClick={() => {
                    onChange([
                      ...value.slice(0, i - 1),
                      value[i],
                      value[i - 1],
                      ...value.slice(i + 1)
                    ])
                  }}
                />
                <IconButton
                  aria-label="Move this element after the next one"
                  className="ms-2"
                  disabled={readOnly || i === value.length - 1}
                  icon={faChevronDown}
                  variant="light"
                  onClick={() => {
                    onChange([
                      ...value.slice(0, i),
                      value[i + 1],
                      value[i],
                      ...value.slice(i + 2)
                    ])
                  }}
                />

                <IconButton
                  aria-label="Move this element out of panel"
                  className="ms-2"
                  disabled={readOnly}
                  icon={faArrowRightFromBracket}
                  variant="light"
                  onClick={() => {
                    onChange(
                      [
                        ...value.slice(0, i),
                        ...value.slice(i + 1)
                      ],
                      value[i]
                    )
                  }}
                />

                <IconButton
                  aria-label="Delete this element"
                  className="ms-2"
                  disabled={readOnly}
                  icon={faTimes}
                  variant="light"
                  onClick={() => {
                    onChange([
                      ...value.slice(0, i),
                      ...value.slice(i + 1)
                    ])
                  }}
                />
              </div>
            </li>
          )
        })}
      </ol>
    </>
  )
}
