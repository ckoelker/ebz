import type { Meta, StoryObj } from '@storybook/vue3-vite'
import KontakthistorieListe from './KontakthistorieListe.vue'
import EinwilligungTabelle from './EinwilligungTabelle.vue'
import WeiterbildungStundenkonto from './WeiterbildungStundenkonto.vue'
import { AKTIVITAETEN, PERSONEN, WEITERBILDUNG } from '../../mock/data'

const meta: Meta = { title: 'Tabs/Detail-Inhalte' }
export default meta
type Story = StoryObj

export const Kontakthistorie: Story = {
  render: () => ({
    components: { KontakthistorieListe },
    setup: () => ({ akts: AKTIVITAETEN }),
    template: '<div class="max-w-2xl"><KontakthistorieListe :aktivitaeten="akts" :mit-person="true" /></div>',
  }),
}

export const Einwilligung: Story = {
  name: 'EinwilligungTabelle (DSGVO)',
  render: () => ({
    components: { EinwilligungTabelle },
    setup: () => ({ e: [...PERSONEN[0].einwilligungen, ...PERSONEN[2].einwilligungen, ...PERSONEN[3].einwilligungen] }),
    template: '<div class="max-w-3xl"><EinwilligungTabelle :einwilligungen="e" /></div>',
  }),
}

export const WeiterbildungGruen: Story = {
  name: 'WeiterbildungStundenkonto — Ampel gelb (14/20)',
  render: () => ({
    components: { WeiterbildungStundenkonto },
    setup: () => ({ d: WEITERBILDUNG.p1 }),
    template: '<div class="max-w-xl"><WeiterbildungStundenkonto :daten="d" /></div>',
  }),
}

export const WeiterbildungRot: Story = {
  name: 'WeiterbildungStundenkonto — Ampel rot (4/20)',
  render: () => ({
    components: { WeiterbildungStundenkonto },
    setup: () => ({ d: WEITERBILDUNG.p3 }),
    template: '<div class="max-w-xl"><WeiterbildungStundenkonto :daten="d" /></div>',
  }),
}
