import { type SVGProps } from 'react'

export function IconLayoutTabs(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      data-name='icon-layout-tabs'
      xmlns='http://www.w3.org/2000/svg'
      viewBox='0 0 79.86 51.14'
      {...props}
    >
      <rect x={5.84} y={5.2} width={15} height={40} rx={2} opacity={0.8} />
      <rect x={25} y={5.89} width={49.1} height={3} rx={1.5} opacity={0.9} />
      <path d='M25 12h15a2 2 0 012 2v4H25z' opacity={0.8} />
      <path d='M43 12h14a2 2 0 012 2v4H43z' opacity={0.5} />
      <path d='M60 12h12a2 2 0 012 2v4H60z' opacity={0.35} />
      <rect x={25} y={20} width={49.1} height={24.8} rx={2} opacity={0.4} />
      <g fill='#fff' opacity={0.72}>
        <circle cx={10.5} cy={11} r={2} />
        <rect x={9} y={18} width={8.5} height={2} rx={1} />
        <rect x={9} y={24} width={7} height={2} rx={1} />
      </g>
    </svg>
  )
}
