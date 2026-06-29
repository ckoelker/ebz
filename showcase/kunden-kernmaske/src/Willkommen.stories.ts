import type { Meta, StoryObj } from '@storybook/vue3-vite'

const meta: Meta = {
  title: 'Willkommen',
  parameters: { layout: 'fullscreen' },
}

export default meta
type Story = StoryObj

export const Übersicht: Story = {
  render: () => ({
    template: `<div class="max-w-2xl p-2 space-y-4">
      <h1 class="text-2xl font-bold text-highlighted">EBZ Kunden-Design-System</h1>
      <p class="text-muted">Storybook für die <strong>kundennahen</strong> Oberflächen — Shop (storefront) und
        Außenportal. Best-practice: interaktive Controls, Autodocs, a11y, Interaction-Tests.</p>
      <ul class="list-disc pl-5 space-y-1 text-sm">
        <li><strong>Tokens</strong> — Farben/Radien der EBZ-Kundenmarke (aktuell Navy).</li>
        <li><strong>UI</strong> — prop-reine Kunden-Primitive (StatusBadge, PreisBadge), die ausschließlich den
          geteilten Domain-Core (<code>@crm-ui/domain</code>) nutzen — nicht die internen Admin-Komponenten.</li>
      </ul>
      <p class="text-sm text-muted">Internes CRM-/Admin-Design-System: eigenes Storybook <code>crm-kernmaske</code>.
        Naht bewusst getrennt: kundennah/Marketing vs. intern/langläufig.</p>
    </div>`,
  }),
}
