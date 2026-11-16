type PortalIconName = 'arrow-right' | 'arrow-up-right' | 'building' | 'car' | 'check' | 'check-circle' | 'file' | 'id' | 'mail' | 'menu' | 'shield' | 'spark' | 'x-circle'

const paths: Record<PortalIconName, React.ReactNode> = {
  'arrow-right': <><path d="M4.75 12h14.5" /><path d="m13 5.75 6.25 6.25L13 18.25" /></>,
  'arrow-up-right': <><path d="M7 17 17 7" /><path d="M8 7h9v9" /></>,
  building: <><path d="M4 20h16" /><path d="M6 20V5.5L12 3l6 2.5V20" /><path d="M9 8h.01M15 8h.01M9 12h.01M15 12h.01M11 20v-4h2v4" /></>,
  car: <><path d="m4 11 1.7-4.2A2 2 0 0 1 7.56 5.5h8.88a2 2 0 0 1 1.86 1.3L20 11" /><path d="M3.5 11h17v6a1.5 1.5 0 0 1-1.5 1.5h-14A1.5 1.5 0 0 1 3.5 17z" /><path d="M7 15h.01M17 15h.01M6 18.5v1M18 18.5v1" /></>,
  check: <path d="m5 12.5 4.5 4.5L19 7.5" />,
  'check-circle': <><circle cx="12" cy="12" r="9" /><path d="m8 12.25 2.6 2.6L16.5 9" /></>,
  file: <><path d="M7 3.75h7l4 4V20a1.25 1.25 0 0 1-1.25 1.25h-9.5A1.25 1.25 0 0 1 6 20V5a1.25 1.25 0 0 1 1-1.25Z" /><path d="M14 4v4h4M9 13h6M9 16.5h6" /></>,
  id: <><rect x="3.5" y="5.5" width="17" height="13" rx="2" /><circle cx="8.5" cy="11" r="1.75" /><path d="M6 15.25c.65-1.4 1.5-2.1 2.5-2.1s1.85.7 2.5 2.1M13.5 10h4M13.5 13.5h4" /></>,
  mail: <><rect x="3.5" y="5.5" width="17" height="13" rx="2" /><path d="m4.5 7 7.5 6 7.5-6" /></>,
  menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
  shield: <><path d="M12 3 19 6v5.25c0 4.3-2.9 7.55-7 9.75-4.1-2.2-7-5.45-7-9.75V6z" /><path d="m9 12 2 2 4-4" /></>,
  spark: <><path d="m12 3-1.25 5.25L6 10l4.75 1.75L12 17l1.25-5.25L18 10l-4.75-1.75z" /><path d="m19 15-.65 2.35L16 18l2.35.65L19 21l.65-2.35L22 18l-2.35-.65z" /></>,
  'x-circle': <><circle cx="12" cy="12" r="9" /><path d="m9 9 6 6m0-6-6 6" /></>,
}

export function PortalIcon({
  name,
  className,
}: {
  name: PortalIconName
  className?: string
}) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.7"
      viewBox="0 0 24 24"
    >
      {paths[name]}
    </svg>
  )
}
