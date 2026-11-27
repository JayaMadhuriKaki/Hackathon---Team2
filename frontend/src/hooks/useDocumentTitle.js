import { useEffect } from 'react'

/** Sets "<Screen> · ExpenseFlow" and moves focus to the page <h1> (SPEC §3). */
export function useDocumentTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · ExpenseFlow` : 'ExpenseFlow'
    const h1 = document.querySelector('main h1')
    if (h1) {
      h1.setAttribute('tabindex', '-1')
      h1.focus({ preventScroll: true })
    }
  }, [title])
}
