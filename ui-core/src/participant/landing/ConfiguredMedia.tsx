import classNames from 'classnames'
import React, { CSSProperties } from 'react'
import { useApiContext } from '../../participant/ApiProvider'

import { requireOptionalString, requirePlainObject, requireOptionalNumber }
  from '../../participant/util/validationUtils'

export type VideoConfig = {
  videoLink: string
  alt?: string
  className?: string
  style?: CSSProperties
}

export type ImageConfig = {
  cleanFileName: string
  version: number
  alt?: string
  className?: string
  style?: CSSProperties
}

export type MediaConfig = ImageConfig | VideoConfig

// TODO: Add JSDoc
// eslint-disable-next-line jsdoc/require-jsdoc
export const validateMediaConfig = (imageConfig: unknown): MediaConfig => {
  const message = 'Invalid image config'
  const config = requirePlainObject(imageConfig, message)

  const cleanFileName = requireOptionalString(config, 'cleanFileName', message)
  const version = requireOptionalNumber(config, 'version', message)
  const alt = requireOptionalString(config, 'alt', message)
  const className = requireOptionalString(config, 'className', message)
  const videoLink = requireOptionalString(config, 'videoLink', message)
  // Only validate that style is an object. React will handle invalid keys.
  const style = config.style ? requirePlainObject(config.style, `${message}: Invalid style`) : undefined

  return {
    cleanFileName,
    version,
    alt,
    className,
    style,
    videoLink
  } as MediaConfig
}

type ConfiguredImageProps = {
  media: MediaConfig
  className?: string
  style?: CSSProperties
}

/** renders an image that is part of a SiteContent spec */
export default function ConfiguredMedia(props: ConfiguredImageProps) {
  const { media, className, style } = props
  const { getImageUrl } = useApiContext()
  if ((media as VideoConfig).videoLink) {
    const videoLinkMedia = media as VideoConfig
    const videoAllowed = isVideoLinkAllowed(videoLinkMedia.videoLink)
    return <div style={{ ...style, ...media.style }}
      className={classNames('configured-image', className, media.className)}>
      {videoAllowed && <iframe src={videoLinkMedia.videoLink} frameBorder="0" allowFullScreen={true}
        data-testid="media-iframe"></iframe> }
      {!videoAllowed && <span className="text-danger">Disallowed video source</span> }
    </div>
  }
  return <img
    src={getImageUrl((media as ImageConfig).cleanFileName, (media as ImageConfig).version)}
    alt={media.alt}
    className={classNames('configured-image', className, media.className)}
    loading="lazy"
    style={{ ...style, ...media.style }}
  />
}

const ALLOWED_VIDEO_HOSTS = ['youtube.com', 'youtu.be', 'vimeo.com']

/**  we don't want to enable arbitrary iframe content on our pages */
const isVideoLinkAllowed = (videoLink: string) => {
  try {
    const url = new URL(videoLink)
    return ALLOWED_VIDEO_HOSTS.some(host => url.host.endsWith(host))
  } catch (e) {
    return false
  }
}
